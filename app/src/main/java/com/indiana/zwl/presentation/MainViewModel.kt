package com.indiana.zwl.presentation

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import com.indiana.zwl.domain.util.PoiUiGroup
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.domain.util.uiGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.usecase.GetForestBansUseCase
import com.indiana.zwl.domain.usecase.SyncForestBansUseCase

data class SelectedZoneDetails(
    val zone: Zone,
    val distanceMeters: Double?,
    val fireRiskLevel: Int,
    val isLoadingFireRisk: Boolean,
    val forestStand: com.indiana.zwl.domain.model.ForestStandSummary? = null,
    val isLoadingForestStand: Boolean = false
)

data class SelectedPoiDetails(
    val poi: Poi,
    val distanceMeters: Double?
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
    private val poiRepository: PoiRepository,
    private val locationRepository: LocationRepository,
    private val compassRepository: CompassRepository,
    private val syncZonesUseCase: SyncZonesUseCase,
    private val syncPoiUseCase: SyncPoiUseCase,
    private val syncForestBansUseCase: SyncForestBansUseCase,
    private val getForestBansUseCase: GetForestBansUseCase,
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getZonesUseCase: GetZonesUseCase,
    private val spatialEngine: SpatialEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _selectedForestBan = MutableStateFlow<ForestBan?>(null)
    val selectedForestBan: StateFlow<ForestBan?> = _selectedForestBan

    fun selectForestBan(ban: ForestBan) {
        _selectedForestBan.value = ban
    }

    fun clearSelectedForestBan() {
        _selectedForestBan.value = null
    }

    val forestBans: StateFlow<List<ForestBan>> = getForestBansUseCase.asFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _debugInvertZone = MutableStateFlow(false)
    val debugInvertZone: StateFlow<Boolean> = _debugInvertZone.asStateFlow()

    fun toggleDebugInvertZone() {
        if (!com.indiana.zwl.BuildConfig.DEBUG) return
        _debugInvertZone.value = !_debugInvertZone.value
    }

    private val _debugForestBanOverride = MutableStateFlow<DebugLocationOverride?>(null)
    val debugForestBanOverride: StateFlow<DebugLocationOverride?> = _debugForestBanOverride.asStateFlow()

    fun debugOverrideLocationToBan(ban: ForestBan) {
        if (!com.indiana.zwl.BuildConfig.DEBUG) return
        viewModelScope.launch(Dispatchers.Default) {
            val geom = org.locationtech.jts.io.WKTReader().read(ban.geometryWkt)!!
            val centroid = geom.getCentroid()
            val lat = centroid.getY()
            val lon = centroid.getX()
            val status = spatialEngine.checkLocation(lat, lon)
            _debugForestBanOverride.value = DebugLocationOverride(
                locationStatus = status,
                forestBan = ban,
                latitude = lat,
                longitude = lon
            )
        }
    }

    fun debugClearBanOverride() {
        _debugForestBanOverride.value = null
    }

    private val sharedPrefs = context.getSharedPreferences("zwl_map_settings", Context.MODE_PRIVATE)

    private val _showForestBans = MutableStateFlow(sharedPrefs.getBoolean("show_forest_bans", true))
    val showForestBans: StateFlow<Boolean> = _showForestBans

    fun setShowForestBans(show: Boolean) {
        _showForestBans.value = show
        sharedPrefs.edit().putBoolean("show_forest_bans", show).apply()
    }

    private val _showPoiGroups = MutableStateFlow(
        PoiUiGroup.entries.associateWith { sharedPrefs.getBoolean("show_poi_${it.key}", true) }
    )
    val showPoiGroups: StateFlow<Map<PoiUiGroup, Boolean>> = _showPoiGroups.asStateFlow()

    fun setShowPoiGroup(group: PoiUiGroup, show: Boolean) {
        _showPoiGroups.value = _showPoiGroups.value + (group to show)
        sharedPrefs.edit().putBoolean("show_poi_${group.key}", show).apply()
    }

    val showAccommodation: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.ACCOMMODATION] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showRest: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.REST] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showShelters: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.SHELTER] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showFireplaces: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.FIREPLACE] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showViewpoints: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.VIEWPOINT] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showParking: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.PARKING] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showEducation: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.EDUCATION] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showOthers: StateFlow<Boolean> = _showPoiGroups.map { it[PoiUiGroup.OTHER] == true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val pois: StateFlow<List<Poi>> = combine(
        poiRepository.getAllPois(),
        _showPoiGroups
    ) { allPois, showGroups ->
        allPois.filter { poi -> showGroups[poi.classify().uiGroup()] == true }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setShowAccommodation(show: Boolean) = setShowPoiGroup(PoiUiGroup.ACCOMMODATION, show)
    fun setShowRest(show: Boolean) = setShowPoiGroup(PoiUiGroup.REST, show)
    fun setShowShelters(show: Boolean) = setShowPoiGroup(PoiUiGroup.SHELTER, show)
    fun setShowFireplaces(show: Boolean) = setShowPoiGroup(PoiUiGroup.FIREPLACE, show)
    fun setShowViewpoints(show: Boolean) = setShowPoiGroup(PoiUiGroup.VIEWPOINT, show)
    fun setShowParking(show: Boolean) = setShowPoiGroup(PoiUiGroup.PARKING, show)
    fun setShowEducation(show: Boolean) = setShowPoiGroup(PoiUiGroup.EDUCATION, show)
    fun setShowOthers(show: Boolean) = setShowPoiGroup(PoiUiGroup.OTHER, show)

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

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncPoiUseCase()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val localBans = getForestBansUseCase()
                    withContext(Dispatchers.Default) {
                        spatialEngine.initializeBans(localBans)
                    }
                    val syncResult = syncForestBansUseCase()
                    if (syncResult.isSuccess) {
                        val updatedBans = syncResult.getOrNull() ?: emptyList()
                        withContext(Dispatchers.Default) {
                            spatialEngine.initializeBans(updatedBans)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

            try {
                val count = zoneRepository.getZonesCount()
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
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val locationWithStatusFlow = (locationRepository.locationFlow as Flow<Location?>)
            .onStart { emit(null) }
            .mapLatest { location ->
                if (location != null) {
                    val (status, ban) = withContext(Dispatchers.Default) {
                        val st = spatialEngine.checkLocation(location.latitude, location.longitude)
                        val bn = spatialEngine.checkForestBan(location.latitude, location.longitude)
                        st to bn
                    }
                    data class LocationData(val location: Location, val status: LocationStatus, val ban: ForestBan?)
                    LocationData(location, status, ban)
                } else {
                    null
                }
            }

        trackingJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    locationWithStatusFlow.collect { data ->
                        if (data != null) {
                            val location = data.location
                            val lastLoc = lastFireRiskLocation
                            if (lastLoc == null || location.distanceTo(lastLoc) > 1000f) {
                                launch(Dispatchers.IO) {
                                    fetchFireHazard(location, data.status)
                                }
                            }

                            _uiState.value = MainUiState.Success(
                                locationStatus = data.status,
                                fireRiskLevel = currentFireRisk,
                                latitude = data.location.latitude,
                                longitude = data.location.longitude,
                                currentForestBan = data.ban
                            )
                        } else {
                            _uiState.value = MainUiState.Success(
                                locationStatus = LocationStatus.EmptyData,
                                fireRiskLevel = -1,
                                latitude = null,
                                longitude = null,
                                currentForestBan = null
                            )
                        }
                    }
                }
                launch {
                    compassRepository.azimuthFlow.collect { azimuth ->
                        _azimuth.value = azimuth
                    }
                }
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
        val result = getFireRiskUseCase(location.latitude, location.longitude)
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
                val timestamp = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    zoneRepository.updateFireRisk(district, code, timestamp)
                }
                updateZoneFireRiskInMemory(district, code, timestamp)
            }
        } else {
            val exception = result.exceptionOrNull()
            if (!isNetworkException(exception)) {
                _debugError.value = "fetchFireHazard API error:\n" + exception?.stackTraceToString()
            } else {
                exception?.printStackTrace()
            }
            if (isNetworkException(exception)) {
                val cached = if (district != null) {
                    withContext(Dispatchers.IO) { zoneRepository.getByForestDistrict(district) }
                } else {
                    null
                }
                currentFireRisk = resolveCachedFireRisk(cached?.fireRiskLevel, cached?.fireRiskTimestamp)
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

    private fun resolveCachedFireRisk(level: Int?, timestamp: Long?): Int {
        val now = System.currentTimeMillis()
        return if (level != null && level in 0..3 &&
            timestamp != null && now - timestamp < FIRE_RISK_CACHE_MAX_AGE_MS
        ) {
            level + 10
        } else {
            -2
        }
    }

    private fun updateZoneFireRiskInMemory(forestDistrict: String, level: Int, timestamp: Long) {
        zones = zones.map { zone ->
            if (zone.forestDistrict.equals(forestDistrict, ignoreCase = true)) {
                zone.copy(fireRiskLevel = level, fireRiskTimestamp = timestamp)
            } else {
                zone
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    fun setDebugError(msg: String) {
        _debugError.value = msg
    }

    fun clearDebugError() {
        _debugError.value = null
    }

    companion object {
        private const val FIRE_RISK_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

sealed interface DownloadEvent {
    data class ToastMessage(val message: String, val isLong: Boolean = false) : DownloadEvent
}
