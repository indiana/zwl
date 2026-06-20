package com.indiana.zwl.presentation

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val zoneDao: ZoneDao,
    private val locationRepository: LocationRepository,
    private val compassRepository: CompassRepository,
    private val syncZonesUseCase: SyncZonesUseCase,
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val spatialEngine: SpatialEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private var hasLocationPermission = false
    private var isEngineInitialized = false
    private var trackingJob: Job? = null

    private var currentFireRisk = -1
    private var lastFireRiskLocation: Location? = null
    var zones: List<ZoneEntity> = emptyList()
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
                        val zones = withContext(Dispatchers.IO) { zoneDao.getAllZones() }
                        this@MainViewModel.zones = zones
                        withContext(Dispatchers.Default) { spatialEngine.initialize(zones) }
                        isEngineInitialized = true
                        if (hasLocationPermission) startTracking() else _uiState.value = MainUiState.PermissionsRequired
                    } else {
                        _uiState.value = MainUiState.EmptyDatabaseRequired
                        isEngineInitialized = false
                    }
                } else {
                    var zones = withContext(Dispatchers.IO) { zoneDao.getAllZones() }
                    if (zones.any { it.forestDistrict.contains("Nieznane", ignoreCase = true) }) {
                        val syncResult = syncZonesUseCase()
                        if (syncResult.isSuccess) {
                            zones = withContext(Dispatchers.IO) { zoneDao.getAllZones() }
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

        trackingJob = viewModelScope.launch {
            combine(
                locationRepository.locationFlow,
                compassRepository.azimuthFlow
            ) { location, azimuth ->
                Pair(location, azimuth)
            }.collectLatest { (location, azimuth) ->
                processTrackingUpdate(location, azimuth)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        locationRepository.stopLocationUpdates()
        compassRepository.stopListening()
    }

    private suspend fun processTrackingUpdate(location: Location, azimuth: Float) {
        val status = spatialEngine.checkLocation(location.latitude, location.longitude)

        val lastLoc = lastFireRiskLocation
        if (lastLoc == null || location.distanceTo(lastLoc) > 1000f) {
            fetchFireHazard(location)
        }

        _uiState.value = MainUiState.Success(
            locationStatus = status,
            fireRiskLevel = currentFireRisk,
            azimuth = azimuth,
            latitude = location.latitude,
            longitude = location.longitude
        )
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
}
