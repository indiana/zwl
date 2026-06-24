package com.indiana.zwl.presentation

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.indiana.zwl.presentation.map.OfflineMapDownloader
import com.indiana.zwl.presentation.map.DownloadStatus
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.map.layer.cache.TileCache
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.distance.DistanceOp

data class SelectedZoneDetails(
    val zone: Zone,
    val distanceMeters: Double?,
    val fireRiskLevel: Int,
    val isLoadingFireRisk: Boolean
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val zoneDao: ZoneDao,
    private val locationRepository: LocationRepository,
    private val compassRepository: CompassRepository,
    private val syncZonesUseCase: SyncZonesUseCase,
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getZonesUseCase: GetZonesUseCase,
    private val spatialEngine: SpatialEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _isDownloadingArea = MutableStateFlow(false)
    val isDownloadingArea: StateFlow<Boolean> = _isDownloadingArea

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _downloadText = MutableStateFlow("")
    val downloadText: StateFlow<String> = _downloadText

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent = _downloadEvent.asSharedFlow()

    private val _selectedZoneDetails = MutableStateFlow<SelectedZoneDetails?>(null)
    val selectedZoneDetails: StateFlow<SelectedZoneDetails?> = _selectedZoneDetails

    private var hasLocationPermission = false
    private var isEngineInitialized = false
    private var trackingJob: Job? = null

    private var currentFireRisk = -1
    private var lastFireRiskLocation: Location? = null
    var zones: List<Zone> = emptyList()
        private set

    init {
        loadZonesAndInitializeEngine()
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        hasLocationPermission = granted
        if (!granted) {
            _uiState.value = MainUiState.PermissionsRequired
            stopTracking()
        } else {
            _uiState.value = MainUiState.Loading
            if (isEngineInitialized) {
                startTracking()
            }
        }
    }

    private fun loadZonesAndInitializeEngine() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                val count = withContext(Dispatchers.IO) { zoneDao.getZonesCount() }
                if (count == 0) {
                    val syncResult = syncZonesUseCase()
                    if (syncResult.isSuccess) {
                        val zones = getZonesUseCase()
                        this@MainViewModel.zones = zones
                        withContext(Dispatchers.Default) { spatialEngine.initialize(zones) }
                        isEngineInitialized = true
                        if (hasLocationPermission) startTracking() else _uiState.value = MainUiState.PermissionsRequired
                    } else {
                        _uiState.value = MainUiState.EmptyDatabaseRequired
                        isEngineInitialized = false
                    }
                } else {
                    var zones = getZonesUseCase()
                    if (zones.any { it.forestDistrict.contains("Nieznane", ignoreCase = true) }) {
                        val syncResult = syncZonesUseCase()
                        if (syncResult.isSuccess) {
                            zones = getZonesUseCase()
                        }
                    }
                    this@MainViewModel.zones = zones
                    withContext(Dispatchers.Default) { spatialEngine.initialize(zones) }
                    isEngineInitialized = true
                    if (hasLocationPermission) startTracking() else _uiState.value = MainUiState.PermissionsRequired
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Wystąpił nieoczekiwany błąd podczas inicjalizacji danych.")
            }
        }
    }

    fun retryDatabaseLoad() {
        loadZonesAndInitializeEngine()
    }

    fun startTracking() {
        if (!hasLocationPermission || !isEngineInitialized) return

        stopTracking()
        locationRepository.startLocationUpdates()
        compassRepository.startListening()

        val locationWithStatusFlow = locationRepository.locationFlow.mapLatest { location ->
            val status = withContext(Dispatchers.Default) {
                spatialEngine.checkLocation(location.latitude, location.longitude)
            }
            val lastLoc = lastFireRiskLocation
            if (lastLoc == null || location.distanceTo(lastLoc) > 1000f) {
                fetchFireHazard(location)
            }
            Triple(location, status, currentFireRisk)
        }

        trackingJob = viewModelScope.launch {
            combine(
                locationWithStatusFlow,
                compassRepository.azimuthFlow
            ) { (location, status, fireRisk), azimuth ->
                MainUiState.Success(
                    locationStatus = status,
                    fireRiskLevel = fireRisk,
                    azimuth = azimuth,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        locationRepository.stopLocationUpdates()
        compassRepository.stopListening()
    }

    private suspend fun fetchFireHazard(location: Location) {
        val result = getFireRiskUseCase(location)
        if (result.isSuccess) {
            currentFireRisk = result.getOrDefault(-1)
            lastFireRiskLocation = location
        } else {
            currentFireRisk = -1
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    fun downloadMapArea(bbox: BoundingBox, tileSize: Int, tileCache: TileCache) {
        viewModelScope.launch {
            OfflineMapDownloader.downloadArea(bbox, tileSize, tileCache).collect { status ->
                when (status) {
                    is DownloadStatus.Start -> {
                        _isDownloadingArea.value = true
                        _downloadProgress.value = 0f
                        _downloadText.value = "Rozpoczynanie pobierania..."
                    }
                    is DownloadStatus.Progress -> {
                        _downloadProgress.value = status.progress
                        _downloadText.value = status.text
                    }
                    is DownloadStatus.Finished -> {
                        _isDownloadingArea.value = false
                        _downloadEvent.emit(DownloadEvent.ToastMessage(
                            "Pobrano pomyślnie ${status.successCount} z ${status.total} kafelków do cache offline!",
                            isLong = true
                        ))
                    }
                    is DownloadStatus.Message -> {
                        _isDownloadingArea.value = false
                        _downloadEvent.emit(DownloadEvent.ToastMessage(status.msg, isLong = true))
                    }
                }
            }
        }
    }

    fun selectZone(zone: Zone, jtsPolygon: org.locationtech.jts.geom.Geometry, clickLat: Double, clickLon: Double) {
        viewModelScope.launch {
            val currentLoc = (uiState.value as? MainUiState.Success)?.let { successState ->
                Location("").apply {
                    latitude = successState.latitude
                    longitude = successState.longitude
                }
            }

            val distance = currentLoc?.let { loc ->
                try {
                    val gf = org.locationtech.jts.geom.GeometryFactory()
                    val userPoint = gf.createPoint(org.locationtech.jts.geom.Coordinate(loc.longitude, loc.latitude))
                    val distanceOp = DistanceOp(jtsPolygon, userPoint)
                    val nearestCoords = distanceOp.nearestPoints()
                    val targetCoord = nearestCoords[0]
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        loc.latitude, loc.longitude,
                        targetCoord.y, targetCoord.x,
                        results
                    )
                    results[0].toDouble()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }

            _selectedZoneDetails.value = SelectedZoneDetails(
                zone = zone,
                distanceMeters = distance,
                fireRiskLevel = -1,
                isLoadingFireRisk = true
            )

            val tempLoc = Location("").apply {
                latitude = clickLat
                longitude = clickLon
            }
            val fireRiskResult = getFireRiskUseCase(tempLoc)
            val riskCode = fireRiskResult.getOrDefault(-1)

            if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                    fireRiskLevel = riskCode,
                    isLoadingFireRisk = false
                )
            }
        }
    }

    fun clearSelectedZone() {
        _selectedZoneDetails.value = null
    }
}

sealed class DownloadEvent {
    data class ToastMessage(val message: String, val isLong: Boolean = false) : DownloadEvent()
}
