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
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import com.google.gson.Gson
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
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.layer.cache.TileCache
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.distance.DistanceOp

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
    private val syncForestBansUseCase: SyncForestBansUseCase,
    private val getForestBansUseCase: GetForestBansUseCase,
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getForestStandUseCase: GetForestStandUseCase,
    private val getZonesUseCase: GetZonesUseCase,
    private val spatialEngine: SpatialEngine,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

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
            val geom = org.locationtech.jts.io.WKTReader().read(ban.geometryWkt)
            val centroid = geom.centroid
            val lat = centroid.y
            val lon = centroid.x
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

    var savedMapCenter: LatLong? = null
        private set
    var savedMapZoom: Byte? = null
        private set

    fun saveMapState(center: LatLong?, zoom: Byte?) {
        if (center != null) savedMapCenter = center
        if (zoom != null) savedMapZoom = zoom
    }

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

            // Asynchroniczne ładowanie i pobieranie zakazów wstępu do lasu w tle
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val localBans = getForestBansUseCase()
                    withContext(Dispatchers.Default) {
                        spatialEngine.initializeBans(localBans)
                    }
                    syncForestBansUseCase()
                    val updatedBans = getForestBansUseCase()
                    withContext(Dispatchers.Default) {
                        spatialEngine.initializeBans(updatedBans)
                    }
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
                    val (status, ban) = withContext(Dispatchers.Default) {
                        val st = spatialEngine.checkLocation(location.latitude, location.longitude)
                        val bn = spatialEngine.checkForestBan(location.latitude, location.longitude)
                        st to bn
                    }
                    val lastLoc = lastFireRiskLocation
                    if (lastLoc == null || location.distanceTo(lastLoc) > 1000f) {
                        fetchFireHazard(location, status)
                    }
                    data class LocationData(val location: Location, val status: LocationStatus, val fireRisk: Int, val ban: ForestBan?)
                    LocationData(location, status, currentFireRisk, ban)
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
                            // Aktualizujemy odległość do wybranego POI na żywo w tle
                            _selectedPoiDetails.value?.let { currentPoiDetails ->
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    location.latitude, location.longitude,
                                    currentPoiDetails.poi.latitude, currentPoiDetails.poi.longitude,
                                    results
                                )
                                _selectedPoiDetails.value = currentPoiDetails.copy(distanceMeters = results[0].toDouble())
                            }

                            _uiState.value = MainUiState.Success(
                                locationStatus = data.status,
                                fireRiskLevel = data.fireRisk,
                                latitude = location.latitude,
                                longitude = location.longitude,
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
                val timestamp = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    zoneDao.updateFireRisk(district, code, timestamp)
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
                    withContext(Dispatchers.IO) { zoneDao.getByForestDistrict(district) }
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

    private fun loadCachedForestStand(zone: Zone): com.indiana.zwl.domain.model.ForestStandSummary? {
        val json = zone.forestStandJson ?: return null
        return try {
            Gson().fromJson(json, com.indiana.zwl.domain.model.ForestStandSummary::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun initialZoneDetails(zone: Zone) = SelectedZoneDetails(
        zone = zone,
        distanceMeters = null,
        fireRiskLevel = -1,
        isLoadingFireRisk = true,
        forestStand = null,
        isLoadingForestStand = true
    )

    private fun isForestStandCacheStale(zone: Zone): Boolean {
        val timestamp = zone.forestStandTimestamp ?: return true
        return System.currentTimeMillis() - timestamp > FOREST_STAND_CACHE_MAX_AGE_MS
    }

    private fun updateZoneForestStandInMemory(forestDistrict: String, json: String, timestamp: Long) {
        zones = zones.map { zone ->
            if (zone.forestDistrict.equals(forestDistrict, ignoreCase = true)) {
                zone.copy(forestStandJson = json, forestStandTimestamp = timestamp)
            } else {
                zone
            }
        }
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
        _selectedZoneDetails.value = initialZoneDetails(zone)
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
                            val targetGeom = if (!jtsPolygon.isValid) {
                                try { jtsPolygon.buffer(0.0) } catch (_: Throwable) { jtsPolygon }
                            } else jtsPolygon
                            val distanceOp = DistanceOp(targetGeom, userPoint)
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

                val cachedForestStand = loadCachedForestStand(zone)
                val needsForestStandRefresh = cachedForestStand == null || isForestStandCacheStale(zone)

                if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                    _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                        distanceMeters = distance,
                        forestStand = cachedForestStand,
                        isLoadingForestStand = needsForestStandRefresh
                    )
                }

                val tempLoc = Location("").apply {
                    latitude = clickLat
                    longitude = clickLon
                }

                val fireRiskResult = getFireRiskUseCase(tempLoc)
                val riskCode = if (fireRiskResult.isSuccess) {
                    val code = fireRiskResult.getOrDefault(-1)
                    if (code in 0..3) {
                        val timestamp = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            zoneDao.updateFireRisk(zone.forestDistrict, code, timestamp)
                        }
                        updateZoneFireRiskInMemory(zone.forestDistrict, code, timestamp)
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
                        val freshZone = withContext(Dispatchers.IO) { zoneDao.getByForestDistrict(zone.forestDistrict) }
                        resolveCachedFireRisk(freshZone?.fireRiskLevel, freshZone?.fireRiskTimestamp)
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

                if (needsForestStandRefresh) {
                    val forestStandResult = getForestStandUseCase(zone)
                    if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                        if (forestStandResult.isSuccess) {
                            val summary = forestStandResult.getOrNull()
                            if (summary != null) {
                                val json = Gson().toJson(summary)
                                val timestamp = System.currentTimeMillis()
                                withContext(Dispatchers.IO) {
                                    zoneDao.updateForestStand(zone.forestDistrict, json, timestamp)
                                }
                                updateZoneForestStandInMemory(zone.forestDistrict, json, timestamp)
                            }
                            _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                                forestStand = summary,
                                isLoadingForestStand = false
                            )
                        } else {
                            val exception = forestStandResult.exceptionOrNull()
                            if (!isNetworkException(exception)) {
                                _debugError.value = "selectZone forest stand API error:\n" + exception?.stackTraceToString()
                            }
                            _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                                isLoadingForestStand = false
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                _debugError.value = "selectZone coroutine error:\n" + e.stackTraceToString()
            }
        }
    }

    fun selectZoneByDistrict(districtName: String) {
        val zone = zones.firstOrNull { it.forestDistrict.equals(districtName, ignoreCase = true) } ?: return
        _selectedZoneDetails.value = initialZoneDetails(zone)
        viewModelScope.launch {
            try {
                val (jtsPolygon, lat, lon) = withContext(Dispatchers.Default) {
                    val polygon = WKTReader().read(zone.geometryWkt)
                    val centroid = polygon.centroid
                    val successState = uiState.value as? MainUiState.Success
                    val useLat = successState?.latitude ?: centroid.y
                    val useLon = successState?.longitude ?: centroid.x
                    Triple(polygon, useLat, useLon)
                }
                selectZone(zone, jtsPolygon, lat, lon)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                _debugError.value = "selectZoneByDistrict error:\n" + e.stackTraceToString()
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

    companion object {
        private const val FIRE_RISK_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val FOREST_STAND_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

sealed class DownloadEvent {
    data class ToastMessage(val message: String, val isLong: Boolean = false) : DownloadEvent()
}
