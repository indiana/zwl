package com.indiana.zwl.presentation.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.presentation.MainUiState
import com.indiana.zwl.presentation.MainViewModel
import com.indiana.zwl.presentation.ZoneDetailViewModel
import com.indiana.zwl.presentation.map.MapViewModel
import com.indiana.zwl.presentation.DownloadEvent
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.util.PoiCategory
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.presentation.theme.ZwlTheme
import com.indiana.zwl.shared.map.MapStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.indiana.zwl.presentation.map.util.isOnline
import com.indiana.zwl.presentation.map.util.rememberIsOnline
import com.indiana.zwl.presentation.map.util.GeometryCache
import com.indiana.zwl.presentation.map.util.buildZoneGeoJson
import com.indiana.zwl.presentation.map.util.buildBanGeoJson
import com.indiana.zwl.presentation.map.util.buildPoiGeoJson
import com.indiana.zwl.presentation.map.util.buildUserArrowGeoJson
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation

@Composable
fun MapViewContainer(
    viewModel: MainViewModel,
    zoneDetailViewModel: ZoneDetailViewModel,
    mapViewModel: MapViewModel,
    zones: List<Zone>,
    isActive: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val selectedZone by zoneDetailViewModel.selectedZoneDetails.collectAsState()
    val selectedPoi by zoneDetailViewModel.selectedPoiDetails.collectAsState()
    val pois by viewModel.pois.collectAsState()
    val showFireplaces by viewModel.showFireplaces.collectAsState()
    val showShelters by viewModel.showShelters.collectAsState()
    val showOthers by viewModel.showOthers.collectAsState()
    val showForestBans by viewModel.showForestBans.collectAsState()
    val forestBans by viewModel.forestBans.collectAsState()

    val rememberedMapView = remember {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }
    var mapboxMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleInstance by remember { mutableStateOf<Style?>(null) }
    var banSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var poiSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var userSource by remember { mutableStateOf<GeoJsonSource?>(null) }

    var hasCenteredOnStartup by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    val geometryCache = remember { GeometryCache() }
    var zoneGeoJson by remember { mutableStateOf<String?>(null) }
    var banGeoJson by remember { mutableStateOf<String?>(null) }
    var lastAppliedPois by remember { mutableStateOf<List<Poi>?>(null) }

    LaunchedEffect(rememberedMapView, isActive, uiState) {
        val map = mapboxMapInstance ?: return@LaunchedEffect
        if (!isActive || hasCenteredOnStartup) return@LaunchedEffect
        if (mapViewModel.savedMapCenterLat != null) {
            hasCenteredOnStartup = true
            return@LaunchedEffect
        }
        val state = uiState
        if (state is MainUiState.Success) {
            val lat = state.latitude
            val lon = state.longitude
            if (lat != null && lon != null) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(lat, lon))
                    .zoom(15.0)
                    .build()
                hasCenteredOnStartup = true
            }
        }
    }

    LaunchedEffect(forestBans) {
        if (forestBans.isEmpty()) return@LaunchedEffect
        val prep = withContext(Dispatchers.Default) {
            val cache = GeometryCache()
            for (ban in forestBans) {
                cache.addBanPolygon(ban.id, ban.geometryWkt)
            }
            cache.buildBanIndex()
            val json = buildBanGeoJson(cache, forestBans.map { it.id })
            cache to json
        }
        geometryCache.adoptBansFrom(prep.first)
        banGeoJson = prep.second
    }

    LaunchedEffect(showForestBans, forestBans, banGeoJson, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        val hasLayers = style.getLayer("bans-fill") != null
        if (!showForestBans || forestBans.isEmpty()) {
            if (hasLayers) {
                style.removeLayer("bans-fill")
                style.removeLayer("bans-line")
            }
        } else {
            val json = banGeoJson
            if (json != null) {
                banSource?.setGeoJson(json)
            }
            if (!hasLayers) {
                style.addLayerBelow(
                    FillLayer("bans-fill", "bans-source").withProperties(
                        PropertyFactory.fillColor("#D32F2F"),
                        PropertyFactory.fillOpacity(0.35f)
                    ),
                    "poi-layer"
                )
                style.addLayerBelow(
                    LineLayer("bans-line", "bans-source").withProperties(
                        PropertyFactory.lineColor("#FFB71C1C"),
                        PropertyFactory.lineWidth(2f)
                    ),
                    "poi-layer"
                )
            }
        }
    }

    val filteredPois = remember(pois, showFireplaces, showShelters, showOthers) {
        pois.filter { poi ->
            val cat = poi.classify()
            when (cat) {
                PoiCategory.SHELTER -> showShelters
                PoiCategory.FIREPLACE -> showFireplaces
                PoiCategory.OTHER -> showOthers
            }
        }
    }

    LaunchedEffect(zones) {
        if (zones.isEmpty()) return@LaunchedEffect
        val prep = withContext(Dispatchers.Default) {
            val cache = GeometryCache()
            for (zone in zones) {
                cache.addZonePolygon(zone.id, zone.geometryWkt)
            }
            cache.buildZoneIndex()
            val json = buildZoneGeoJson(cache, zones.map { it.id })
            cache to json
        }
        geometryCache.adoptZonesFrom(prep.first)
        zoneGeoJson = prep.second
    }

    LaunchedEffect(zoneGeoJson, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        val json = zoneGeoJson ?: return@LaunchedEffect
        if (style.getLayer("zones-fill") != null) {
            (style.getSource("zones-source") as? GeoJsonSource)?.setGeoJson(json)
        } else {
            style.addSource(GeoJsonSource("zones-source").apply { setGeoJson(json) })
            style.addLayer(FillLayer("zones-fill", "zones-source").withProperties(
                PropertyFactory.fillColor("#1B5E20"),
                PropertyFactory.fillOpacity(0.35f)
            ))
            style.addLayer(LineLayer("zones-line", "zones-source").withProperties(
                PropertyFactory.lineColor("#FF1B5E20"),
                PropertyFactory.lineWidth(2f)
            ))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, rememberedMapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> rememberedMapView.onCreate(null)
                Lifecycle.Event.ON_START -> rememberedMapView.onStart()
                Lifecycle.Event.ON_RESUME -> rememberedMapView.onResume()
                Lifecycle.Event.ON_PAUSE -> rememberedMapView.onPause()
                Lifecycle.Event.ON_STOP -> rememberedMapView.onStop()
                Lifecycle.Event.ON_DESTROY -> rememberedMapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            rememberedMapView.onDestroy()
        }
    }

    val isDownloadingArea by mapViewModel.isDownloadingArea.collectAsState()
    val downloadProgress by mapViewModel.downloadProgress.collectAsState()
    val downloadText by mapViewModel.downloadText.collectAsState()

    LaunchedEffect(mapViewModel) {
        mapViewModel.downloadEvent.collect { event ->
            when (event) {
                is DownloadEvent.ToastMessage -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var lastAzimuth by remember { mutableFloatStateOf(0f) }
    var lastAzimuthTs by remember { mutableLongStateOf(0L) }
    var currentZoom by remember { mutableFloatStateOf(15f) }
    LaunchedEffect(viewModel) {
        viewModel.azimuth.collect { az ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (kotlin.math.abs(az - lastAzimuth) >= 0.5f || now - lastAzimuthTs >= 300L) {
                lastAzimuth = az
                lastAzimuthTs = now
            }
        }
    }
    val userLat = (uiState as? MainUiState.Success)?.latitude
    val userLon = (uiState as? MainUiState.Success)?.longitude

    LaunchedEffect(userLat, userLon, lastAzimuth, currentZoom, userSource) {
        val style = styleInstance ?: return@LaunchedEffect
        if (userLat == null || userLon == null) return@LaunchedEffect
        val lat = userLat
        val latRad = Math.toRadians(lat)
        val metersPerPixel = 156543.03392 * Math.cos(latRad) / Math.pow(2.0, currentZoom.toDouble())
        val scaleMeters = 15.0 * metersPerPixel / 20.0
        var src = userSource
        if (src == null) {
            src = GeoJsonSource("user-source").apply {
                setGeoJson(buildUserArrowGeoJson(lat, userLon!!, lastAzimuth, scaleMeters))
            }
            userSource = src
            style.addSource(src)
            style.addLayer(
                FillLayer("user-layer", "user-source").withProperties(
                    PropertyFactory.fillColor("#007AFF"),
                    PropertyFactory.fillOpacity(1f)
                )
            )
            style.addLayer(
                LineLayer("user-layer-outline", "user-source").withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(3f)
                )
            )
        } else {
            src.setGeoJson(buildUserArrowGeoJson(lat, userLon!!, lastAzimuth, scaleMeters))
        }
    }

    val isOnlineState by rememberIsOnline()
    val isInZone = (uiState as? MainUiState.Success)?.locationStatus is LocationStatus.InZone

    val offlineContext = LocalContext.current
    LaunchedEffect(isOnlineState, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        val offlineLayerId = "osm-offline-layer"
        val existing = style.getLayer(offlineLayerId)
        if (isOnlineState) {
            if (existing != null) {
                style.removeLayer(offlineLayerId)
                style.removeSource("osm-offline")
            }
        } else {
            if (existing == null) {
                val cacheRoot = File(offlineContext.externalCacheDir ?: offlineContext.cacheDir, "mapcache")
                val dbFile = File(cacheRoot, "map.mbtiles")
                val localUrl = "mbtiles://file://${dbFile.absolutePath}"
                style.addSource(RasterSource("osm-offline", localUrl, 256))
                style.addLayerBelow(RasterLayer(offlineLayerId, "osm-offline"), "osm")
            }
        }
    }

    ZwlTheme(isInZone = isInZone) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AndroidView(
                factory = { ctx ->
                    rememberedMapView.apply {
                        getMapAsync { map ->
                            mapboxMapInstance = map

                            map.setMinZoomPreference(MapStyle.MIN_ZOOM)
                            map.setMaxZoomPreference(MapStyle.MAX_ZOOM)

                            map.uiSettings.isRotateGesturesEnabled = false
                            map.uiSettings.isTiltGesturesEnabled = false

                            map.addOnCameraIdleListener {
                                map.cameraPosition?.zoom?.let { currentZoom = it.toFloat() }
                            }

                            val savedLat = mapViewModel.savedMapCenterLat
                            val savedLng = mapViewModel.savedMapCenterLng
                            val savedZoom = mapViewModel.savedMapZoom

                            map.cameraPosition = CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        savedLat ?: MapStyle.DEFAULT_LAT,
                                        savedLng ?: MapStyle.DEFAULT_LNG
                                    )
                                )
                                .zoom(savedZoom ?: MapStyle.DEFAULT_ZOOM)
                                .build()

                            map.setStyle(Style.Builder().fromJson(MapStyle.OSM_STYLE_JSON)) { style ->
                                styleInstance = style

                                val ps = GeoJsonSource("poi-source").apply {
                                    setGeoJson(buildPoiGeoJson(filteredPois))
                                }
                                poiSource = ps
                                style.addSource(ps)
                                style.addLayer(
                                    CircleLayer("poi-layer", "poi-source").withProperties(
                                        PropertyFactory.circleColor(
                                            Expression.match(
                                                Expression.get("category"),
                                                Expression.literal("#1976D2"),
                                                Expression.stop("SHELTER", Expression.literal("#4E342E")),
                                                Expression.stop("FIREPLACE", Expression.literal("#E65100"))
                                            )
                                        ),
                                        PropertyFactory.circleRadius(
                                            Expression.step(
                                                Expression.zoom(),
                                                Expression.literal(2f),
                                                Expression.literal(11.0),
                                                Expression.literal(3f),
                                                Expression.literal(12.5),
                                                Expression.literal(5f),
                                                Expression.literal(14.0),
                                                Expression.literal(8f),
                                                Expression.literal(16.0),
                                                Expression.literal(12f)
                                            )
                                        ),
                                        PropertyFactory.circleStrokeWidth(1.5f),
                                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                                        PropertyFactory.circleOpacity(0.9f)
                                    )
                                )

                                val initialBanGeoJson = buildBanGeoJson(geometryCache, forestBans.map { it.id })
                                val bs = GeoJsonSource("bans-source").apply { setGeoJson(initialBanGeoJson) }
                                banSource = bs
                                style.addSource(bs)

                                map.addOnMapClickListener { point ->
                                    val clickedPoint = geometryCache.createPoint(point.longitude, point.latitude)

                                    val hitZoneId = geometryCache.findZoneIdAt(clickedPoint)
                                    if (hitZoneId != null) {
                                        val zone = zones.firstOrNull { it.id == hitZoneId }
                                        val jtsPoly = zone?.let { geometryCache.parse(it.geometryWkt)?.jtsPolygons?.firstOrNull() }
                                        if (zone != null && jtsPoly != null) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                val successState = uiState as? MainUiState.Success
                                                zoneDetailViewModel.selectZone(
                                                    zone, jtsPoly,
                                                    point.latitude, point.longitude,
                                                    successState?.latitude, successState?.longitude
                                                )
                                            }
                                        }
                                        return@addOnMapClickListener true
                                    }

                                    val hitBanId = geometryCache.findBanIdAt(clickedPoint)
                                    if (hitBanId != null) {
                                        val ban = forestBans.firstOrNull { it.id == hitBanId }
                                        if (ban != null) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                viewModel.selectForestBan(ban)
                                            }
                                        }
                                        return@addOnMapClickListener true
                                    }

                                    val screenPoint = map.projection.toScreenLocation(point)
                                    val hitFeatures = map.queryRenderedFeatures(
                                        android.graphics.RectF(
                                            screenPoint.x - 24f, screenPoint.y - 24f,
                                            screenPoint.x + 24f, screenPoint.y + 24f
                                        ),
                                        "poi-layer"
                                    )
                                    if (hitFeatures.isNotEmpty()) {
                                        val props = hitFeatures[0].properties()
                                        val poiId = props?.get("id")?.asLong
                                        val poi = pois.firstOrNull { it.id == poiId }
                                        if (poi != null) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                val successState = uiState as? MainUiState.Success
                                                zoneDetailViewModel.selectPoi(
                                                    poi,
                                                    successState?.latitude,
                                                    successState?.longitude
                                                )
                                            }
                                            return@addOnMapClickListener true
                                        }
                                    }

                                    false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { mapView ->
                    mapboxMapInstance?.let { map ->
                        val pos = map.cameraPosition
                        mapViewModel.saveMapState(pos.target?.latitude, pos.target?.longitude, pos.zoom)
                    }
                }
            )

            LaunchedEffect(filteredPois, poiSource) {
                val style = styleInstance ?: return@LaunchedEffect
                val src = poiSource ?: return@LaunchedEffect
                if (filteredPois.isEmpty()) {
                    lastAppliedPois = filteredPois
                    if (style.getLayer("poi-layer") != null) {
                        style.removeLayer("poi-layer")
                    }
                } else {
                    if (lastAppliedPois === filteredPois) return@LaunchedEffect
                    val json = withContext(Dispatchers.Default) { buildPoiGeoJson(filteredPois) }
                    src.setGeoJson(json)
                    lastAppliedPois = filteredPois
                    if (style.getLayer("poi-layer") == null) {
                        style.addLayerBelow(
                            CircleLayer("poi-layer", "poi-source").withProperties(
                                PropertyFactory.circleColor(
                                    Expression.match(
                                        Expression.get("category"),
                                        Expression.literal("#1976D2"),
                                        Expression.stop("SHELTER", Expression.literal("#4E342E")),
                                        Expression.stop("FIREPLACE", Expression.literal("#E65100"))
                                    )
                                ),
                                PropertyFactory.circleRadius(
                                    Expression.step(
                                        Expression.zoom(),
                                        Expression.literal(2f),
                                        Expression.literal(11.0),
                                        Expression.literal(3f),
                                        Expression.literal(12.5),
                                        Expression.literal(5f),
                                        Expression.literal(14.0),
                                        Expression.literal(8f),
                                        Expression.literal(16.0),
                                        Expression.literal(12f)
                                    )
                                ),
                                PropertyFactory.circleStrokeWidth(1.5f),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleOpacity(0.9f)
                            ),
                            "zones-fill"
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = !isOnlineState,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OfflineIcon(modifier = Modifier.size(16.dp), color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Tryb offline",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                val state = uiState
                                if (state is MainUiState.Success) {
                                    val lat = state.latitude
                                    val lon = state.longitude
                                    if (lat != null && lon != null) {
                                        mapboxMapInstance?.cameraPosition = CameraPosition.Builder()
                                            .target(LatLng(lat, lon))
                                            .zoom(15.0)
                                            .build()
                                    } else {
                                        Toast.makeText(context, "Oczekiwanie na sygnał GPS...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(48.dp),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Moja lokalizacja"
                            )
                        }

                        Box {
                            FloatingActionButton(
                                onClick = { isSettingsOpen = true },
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(48.dp),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ustawienia mapy"
                                )
                            }
                            DropdownMenu(
                                expanded = isSettingsOpen,
                                onDismissRequest = { isSettingsOpen = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                modifier = Modifier.width(240.dp),
                                offset = androidx.compose.ui.unit.DpOffset(x = (-196).dp, y = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Ustawienia Mapy",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)

                                    Text(
                                        text = "Wyświetlaj na mapie:",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = showForestBans,
                                                onCheckedChange = { viewModel.setShowForestBans(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                                            )
                                            Text(
                                                text = "Zakazy wstępu do lasu",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = showShelters,
                                                onCheckedChange = { viewModel.setShowShelters(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = "Wiaty i wiatopodobne",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = showFireplaces,
                                                onCheckedChange = { viewModel.setShowFireplaces(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = "Miejsca na ognisko",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = showOthers,
                                                onCheckedChange = { viewModel.setShowOthers(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = "Inne punkty",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)

                                    if (isOnlineState) {
                                        Button(
                                            onClick = {
                                                isSettingsOpen = false
                                                if (!isOnline(context)) {
                                                    Toast.makeText(context, "Jesteś w trybie offline", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val map = mapboxMapInstance
                                                val mv = rememberedMapView
                                                if (map != null) {
                                                    val pos = map.cameraPosition
                                                    val center = pos.target
                                                    val zoom = pos.zoom
                                                    if (center != null) {
                                                        val width = mv.width.toDouble()
                                                        val height = mv.height.toDouble()
                                                        val latRad = Math.toRadians(center.latitude)
                                                        val metersPerPixel = 156543.03392 * Math.cos(latRad) / Math.pow(2.0, zoom)
                                                        val latSpan = (height / 2) * metersPerPixel / 111320.0
                                                        val lngSpan = (width / 2) * metersPerPixel / (111320.0 * Math.cos(latRad))

                                                        mapViewModel.downloadMapArea(
                                                            latSouth = center.latitude - latSpan,
                                                            latNorth = center.latitude + latSpan,
                                                            lonWest = center.longitude - lngSpan,
                                                            lonEast = center.longitude + lngSpan,
                                                            cacheDir = File(context.externalCacheDir, "mapcache")
                                                        )
                                                    } else {
                                                        Toast.makeText(context, "Brak widocznego obszaru", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Mapa nie jest gotowa.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isDownloadingArea,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Pobierz obszar",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color.White
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Pobieranie niedostępne w trybie offline",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isSettingsOpen = false
                                            coroutineScope.launch {
                                                val success = withContext(Dispatchers.IO) {
                                                    val cacheDir = File(context.externalCacheDir, "mapcache")
                                                    if (cacheDir.exists()) {
                                                        cacheDir.deleteRecursively()
                                                    } else {
                                                        false
                                                    }
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Pamięć podręczna została wyczyszczona" else "Brak pamięci podręcznej do wyczyszczenia",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Wyczyść cache",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isDownloadingArea) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(220.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Pobieranie mapy offline...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = downloadText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedPoi != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { height -> height / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { height -> height / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedPoi?.let { details ->
                    PoiDetailsCard(
                        details = details,
                        onClose = { zoneDetailViewModel.clearSelectedPoi() },
                        modifier = Modifier.padding(bottom = 88.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width / 2f, height * 0.15f)
            lineTo(width * 0.15f, height * 0.85f)
            lineTo(width * 0.85f, height * 0.85f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                join = StrokeJoin.Round
            )
        )
        drawLine(
            color = color,
            start = Offset(width / 2f, height * 0.4f),
            end = Offset(width / 2f, height * 0.65f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = 1.5.dp.toPx(),
            center = Offset(width / 2f, height * 0.77f)
        )
    }
}
