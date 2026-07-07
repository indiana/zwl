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
import com.indiana.zwl.data.local.PoiDao
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
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

data class SelectedPoiDetails(
    val poi: PoiEntity,
    val distanceMeters: Double?
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val zoneDao: ZoneDao,
    private val poiDao: PoiDao,
    private val locationRepository: LocationRepository,
    private val compassRepository: CompassRepository,
    private val syncZonesUseCase: SyncZonesUseCase,
    private val syncPoiUseCase: SyncPoiUseCase,
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getZonesUseCase: GetZonesUseCase,
    private val spatialEngine: SpatialEngine,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
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

    private val _selectedPoiDetails = MutableStateFlow<SelectedPoiDetails?>(null)
    val selectedPoiDetails: StateFlow<SelectedPoiDetails?> = _selectedPoiDetails

    private val sharedPrefs = context.getSharedPreferences("zwl_map_settings", Context.MODE_PRIVATE)

    private val _showFireplaces = MutableStateFlow(sharedPrefs.getBoolean("show_fireplaces", true))
    val showFireplaces: StateFlow<Boolean> = _showFireplaces

    private val _showShelters = MutableStateFlow(sharedPrefs.getBoolean("show_shelters", true))
    val showShelters: StateFlow<Boolean> = _showShelters

    private val _showOthers = MutableStateFlow(sharedPrefs.getBoolean("show_others", true))
    val showOthers: StateFlow<Boolean> = _showOthers

    val pois: StateFlow<List<PoiEntity>> = combine(
        poiDao.getAllPois(),
        _showFireplaces,
        _showShelters,
        _showOthers
    ) { allPois, showFireplaces, showShelters, showOthers ->
        allPois.filter { poi ->
            val nameLower = poi.name.lowercase(java.util.Locale.getDefault())
            val isWiata = nameLower.contains("wiata") || nameLower.contains("altan") ||
                    nameLower.contains("szałas") || nameLower.contains("shelter")
            val isFireplace = nameLower.contains("ognis") || nameLower.contains("palenis") ||
                    nameLower.contains("fire")
            
            if (isWiata) showShelters
            else if (isFireplace) showFireplaces
            else showOthers
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setShowFireplaces(show: Boolean) {
        _showFireplaces.value = show
        sharedPrefs.edit().putBoolean("show_fireplaces", show).apply()
    }

    fun setShowShelters(show: Boolean) {
        _showShelters.value = show
        sharedPrefs.edit().putBoolean("show_shelters", show).apply()
    }

    fun setShowOthers(show: Boolean) {
        _showOthers.value = show
        sharedPrefs.edit().putBoolean("show_others", show).apply()
    }

    private val _debugError = MutableStateFlow<String?>(null)
    val debugError: StateFlow<String?> = _debugError

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

            // Asynchroniczne pobieranie punktów POI w tle
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncPoiUseCase()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

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
                if (e is CancellationException) throw e
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

        @Suppress("UNCHECKED_CAST")
        val locationWithStatusFlow = (locationRepository.locationFlow as Flow<Location?>)
            .onStart { emit(null) }
            .mapLatest { location ->
                if (location != null) {
                    val status = withContext(Dispatchers.Default) {
                        spatialEngine.checkLocation(location.latitude, location.longitude)
                    }
                    val lastLoc = lastFireRiskLocation
                    if (lastLoc == null || location.distanceTo(lastLoc) > 1000f) {
                        fetchFireHazard(location, status)
                    }
                    Triple(location, status, currentFireRisk)
                } else {
                    Triple(null, LocationStatus.EmptyData, -1)
                }
            }

        trackingJob = viewModelScope.launch {
            combine(
                locationWithStatusFlow,
                compassRepository.azimuthFlow
            ) { (location, status, fireRisk), azimuth ->
                // Aktualizujemy odległość do wybranego POI na żywo w tle
                if (location != null) {
                    _selectedPoiDetails.value?.let { currentPoiDetails ->
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            currentPoiDetails.poi.latitude, currentPoiDetails.poi.longitude,
                            results
                        )
                        _selectedPoiDetails.value = currentPoiDetails.copy(distanceMeters = results[0].toDouble())
                    }
                }

                MainUiState.Success(
                    locationStatus = status,
                    fireRiskLevel = fireRisk,
                    azimuth = azimuth,
                    latitude = location?.latitude,
                    longitude = location?.longitude
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

    private suspend fun fetchFireHazard(location: Location, status: LocationStatus) {
        val result = getFireRiskUseCase(location)
        val district = when (status) {
            is LocationStatus.InZone -> status.forestDistrict
            is LocationStatus.OutsideZone -> status.nearestDistrict
            else -> null
        }
        if (result.isSuccess) {
            val code = result.getOrDefault(-1)
            currentFireRisk = code
            lastFireRiskLocation = location
            if (district != null && code in 0..3) {
                withContext(Dispatchers.IO) {
                    zoneDao.updateFireRisk(district, code, System.currentTimeMillis())
                }
            }
        } else {
            val exception = result.exceptionOrNull()
            if (!isNetworkException(exception)) {
                _debugError.value = "fetchFireHazard API error:\n" + exception?.stackTraceToString()
            } else {
                exception?.printStackTrace()
            }
            if (isNetworkException(exception)) {
                var cachedRisk: Int? = null
                if (district != null) {
                    cachedRisk = withContext(Dispatchers.IO) {
                        zoneDao.getAllZones().find { it.forestDistrict.equals(district, ignoreCase = true) }?.fireRiskLevel
                    }
                }
                currentFireRisk = if (cachedRisk != null && cachedRisk in 0..3) {
                    cachedRisk + 10
                } else {
                    -2
                }
            } else {
                currentFireRisk = -1
            }
        }
    }

    private fun isNetworkException(e: Throwable?): Boolean {
        return e is java.net.UnknownHostException ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException ||
               e is java.net.SocketException ||
               e is javax.net.ssl.SSLException
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    fun downloadMapArea(bbox: BoundingBox, tileSize: Int, tileCache: TileCache) {
        viewModelScope.launch {
            OfflineMapDownloader.downloadArea(bbox, tileSize, tileCache, okHttpClient).collect { status ->
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
            try {
                _selectedPoiDetails.value = null
                val currentLoc = (uiState.value as? MainUiState.Success)?.let { successState ->
                    val lat = successState.latitude
                    val lon = successState.longitude
                    if (lat != null && lon != null) {
                        Location("").apply {
                            latitude = lat
                            longitude = lon
                        }
                    } else {
                        null
                    }
                }

                val distance = currentLoc?.let { loc ->
                    withContext(Dispatchers.Default) {
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
                            if (e is CancellationException) throw e
                            e.printStackTrace()
                            _debugError.value = "Distance calculation error:\n" + e.stackTraceToString()
                            null
                        }
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
                val riskCode = if (fireRiskResult.isSuccess) {
                    val code = fireRiskResult.getOrDefault(-1)
                    if (code in 0..3) {
                        withContext(Dispatchers.IO) {
                            zoneDao.updateFireRiskById(zone.id, code, System.currentTimeMillis())
                        }
                    }
                    code
                } else {
                    val exception = fireRiskResult.exceptionOrNull()
                    if (!isNetworkException(exception)) {
                        _debugError.value = "selectZone fire risk API error:\n" + exception?.stackTraceToString()
                    } else {
                        exception?.printStackTrace()
                    }
                    if (isNetworkException(exception)) {
                        val cachedRisk = zone.fireRiskLevel
                        if (cachedRisk != null && cachedRisk in 0..3) {
                            cachedRisk + 10
                        } else {
                            -2
                        }
                    } else {
                        -1
                    }
                }

                if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                    _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                        fireRiskLevel = riskCode,
                        isLoadingFireRisk = false
                    )
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                _debugError.value = "selectZone coroutine error:\n" + e.stackTraceToString()
            }
        }
    }

    fun setDebugError(msg: String) {
        _debugError.value = msg
    }

    fun clearDebugError() {
        _debugError.value = null
    }

    fun clearSelectedZone() {
        _selectedZoneDetails.value = null
    }

    fun selectPoi(poi: PoiEntity) {
        viewModelScope.launch {
            _selectedZoneDetails.value = null
            val currentLoc = (uiState.value as? MainUiState.Success)?.let { successState ->
                val lat = successState.latitude
                val lon = successState.longitude
                if (lat != null && lon != null) {
                    Location("").apply {
                        latitude = lat
                        longitude = lon
                    }
                } else {
                    null
                }
            }

            val distance = currentLoc?.let { loc ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    poi.latitude, poi.longitude,
                    results
                )
                results[0].toDouble()
            }

            _selectedPoiDetails.value = SelectedPoiDetails(
                poi = poi,
                distanceMeters = distance
            )
        }
    }

    fun clearSelectedPoi() {
        _selectedPoiDetails.value = null
    }
}

sealed class DownloadEvent {
    data class ToastMessage(val message: String, val isLong: Boolean = false) : DownloadEvent()
}
