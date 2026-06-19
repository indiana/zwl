package com.example.zwl.presentation

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.zwl.data.local.ZoneDao
import com.example.zwl.data.remote.BdlFireApi
import com.example.zwl.domain.CompassRepository
import com.example.zwl.domain.LocationRepository
import com.example.zwl.domain.SpatialEngine
import com.example.zwl.domain.model.LocationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val zoneDao: ZoneDao,
    private val locationRepository: LocationRepository,
    private val compassRepository: CompassRepository,
    private val fireApi: BdlFireApi,
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
        } else if (isEngineInitialized) {
            startTracking()
        }
    }

    private fun loadZonesAndInitializeEngine() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            val count = zoneDao.getZonesCount()
            if (count == 0) {
                _uiState.value = MainUiState.EmptyDatabaseRequired
                isEngineInitialized = false
            } else {
                val zones = withContext(Dispatchers.IO) {
                    zoneDao.getAllZones()
                }
                this@MainViewModel.zones = zones
                spatialEngine.initialize(zones)
                isEngineInitialized = true
                if (hasLocationPermission) {
                    startTracking()
                } else {
                    _uiState.value = MainUiState.PermissionsRequired
                }
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
        try {
            val response = fireApi.getFireHazard(location.latitude, location.longitude)
            currentFireRisk = response.riskLevel
            lastFireRiskLocation = location
        } catch (e: Exception) {
            currentFireRisk = -1
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    class Factory(
        private val zoneDao: ZoneDao,
        private val locationRepository: LocationRepository,
        private val compassRepository: CompassRepository,
        private val fireApi: BdlFireApi,
        private val spatialEngine: SpatialEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(zoneDao, locationRepository, compassRepository, fireApi, spatialEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
