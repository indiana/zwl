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
import com.indiana.zwl.presentation.SelectedZoneDetails
import com.indiana.zwl.presentation.SelectedPoiDetails
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.util.PoiCategory
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.presentation.theme.ZwlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Fill
import org.maplibre.android.plugins.annotation.FillManager
import org.maplibre.android.plugins.annotation.FillOptions
import org.maplibre.android.plugins.annotation.Line
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import java.io.File
import com.indiana.zwl.presentation.map.util.isOnline
import com.indiana.zwl.presentation.map.util.rememberIsOnline
import com.indiana.zwl.presentation.map.util.createUserLocationArrowBitmap
import com.indiana.zwl.presentation.map.util.wktToFillLatLngs
import com.indiana.zwl.presentation.map.util.wktToJtsPolygon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var mapboxMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleInstance by remember { mutableStateOf<Style?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    var fillManager by remember { mutableStateOf<FillManager?>(null) }
    var lineManager by remember { mutableStateOf<LineManager?>(null) }
    var userSymbol by remember { mutableStateOf<Symbol?>(null) }
    var zoneFills by remember { mutableStateOf<List<Fill>>(emptyList()) }
    var zoneLines by remember { mutableStateOf<List<Line>>(emptyList()) }
    var banFills by remember { mutableStateOf<List<Fill>>(emptyList()) }
    var banLines by remember { mutableStateOf<List<Line>>(emptyList()) }
    var poiSymbols by remember { mutableStateOf<List<Symbol>>(emptyList()) }
    var fillToZoneMap by remember { mutableStateOf<Map<Long, Zone>>(emptyMap()) }
    var fillToBanMap by remember { mutableStateOf<Map<Long, ForestBan>>(emptyMap()) }

    var hasCenteredOnStartup by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(mapViewInstance, isActive, uiState) {
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

    LaunchedEffect(forestBans, showForestBans, fillManager, lineManager) {
        val fm = fillManager ?: return@LaunchedEffect
        val lm = lineManager ?: return@LaunchedEffect

        if (banFills.isNotEmpty()) {
            fm.delete(banFills)
            banFills = emptyList()
        }
        if (banLines.isNotEmpty()) {
            lm.delete(banLines)
            banLines = emptyList()
        }
        fillToBanMap = emptyMap()

        if (!showForestBans || forestBans.isEmpty()) return@LaunchedEffect

        val newFills = mutableListOf<Fill>()
        val newLines = mutableListOf<Line>()
        val newFillToBan = mutableMapOf<Long, ForestBan>()

        for (ban in forestBans) {
            try {
                val rings = wktToFillLatLngs(ban.geometryWkt)
                if (rings.isEmpty()) continue

                val fillOptions = FillOptions()
                    .withLatLngs(rings)
                    .withFillColor("#4DD32F2F")
                    .withFillOpacity(0.3f)
                val fill = fm.create(fillOptions)
                newFills.add(fill)
                newFillToBan[fill.id] = ban

                val outerRing = rings.first()
                val lineOptions = LineOptions()
                    .withLatLngs(outerRing)
                    .withLineColor("#FFB71C1C")
                    .withLineWidth(2f)
                val line = lm.create(lineOptions)
                newLines.add(line)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        banFills = newFills
        banLines = newLines
        fillToBanMap = newFillToBan
    }

    LaunchedEffect(pois, symbolManager) {
        val sm = symbolManager ?: return@LaunchedEffect

        if (poiSymbols.isNotEmpty()) {
            sm.delete(poiSymbols)
            poiSymbols = emptyList()
        }

        val newSymbols = mutableListOf<Symbol>()
        for (poi in pois) {
            val category = poi.classify()
            val iconName = when (category) {
                PoiCategory.SHELTER -> "poi-shelter"
                PoiCategory.FIREPLACE -> "poi-fireplace"
                PoiCategory.OTHER -> "poi-generic"
            }
            val options = SymbolOptions()
                .withLatLng(LatLng(poi.latitude, poi.longitude))
                .withIconImage(iconName)
                .withIconSize(1.0f)
            val symbol = sm.create(options)
            newSymbols.add(symbol)
        }
        poiSymbols = newSymbols
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
    LaunchedEffect(viewModel) {
        viewModel.azimuth.collect { azimuth ->
            lastAzimuth = azimuth
            val sm = symbolManager ?: return@collect
            val current = userSymbol ?: return@collect
            current.iconRotate = azimuth
            sm.update(current)
        }
    }

    val isOnlineState by rememberIsOnline()
    val isInZone = (uiState as? MainUiState.Success)?.locationStatus is LocationStatus.InZone

    ZwlTheme(isInZone = isInZone) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            mapboxMapInstance = map

                            map.setMinZoomPreference(MapStyle.MIN_ZOOM)
                            map.setMaxZoomPreference(MapStyle.MAX_ZOOM)

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

                            map.setStyle(MapStyle.OSM_STYLE_JSON) { style ->
                                styleInstance = style

                                val arrowBitmap = createUserLocationArrowBitmap(ctx)
                                style.addImage("user-arrow", arrowBitmap)

                                registerPoiIcons(ctx, style)

                                val mv = this@apply
                                symbolManager = SymbolManager(mv, map, style).apply {
                                    iconAllowOverlap = true
                                    iconIgnorePlacement = true
                                }
                                fillManager = FillManager(mv, map, style)
                                lineManager = LineManager(mv, map, style)

                                map.addOnMapClickListener { point ->
                                    val gf = GeometryFactory()
                                    val clickedPoint = gf.createPoint(Coordinate(point.longitude, point.latitude))

                                    var handledZone = false
                                    for ((fillId, zone) in fillToZoneMap) {
                                        val jtsPoly = wktToJtsPolygon(zone.geometryWkt)
                                        if (jtsPoly != null) {
                                            val safePoly = if (!jtsPoly.isValid()) {
                                                try { jtsPoly.buffer(0.0) } catch (_: Throwable) { jtsPoly }
                                            } else jtsPoly
                                            if (safePoly.contains(clickedPoint)) {
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    val successState = uiState as? MainUiState.Success
                                                    zoneDetailViewModel.selectZone(
                                                        zone, jtsPoly,
                                                        point.latitude, point.longitude,
                                                        successState?.latitude, successState?.longitude
                                                    )
                                                }
                                                handledZone = true
                                                break
                                            }
                                        }
                                    }
                                    if (handledZone) return@addOnMapClickListener true

                                    var handledBan = false
                                    for ((fillId, ban) in fillToBanMap) {
                                        val jtsPoly = wktToJtsPolygon(ban.geometryWkt)
                                        if (jtsPoly != null) {
                                            val safePoly = if (!jtsPoly.isValid()) {
                                                try { jtsPoly.buffer(0.0) } catch (_: Throwable) { jtsPoly }
                                            } else jtsPoly
                                            if (safePoly.contains(clickedPoint)) {
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    viewModel.selectForestBan(ban)
                                                }
                                                handledBan = true
                                                break
                                            }
                                        }
                                    }
                                    if (handledBan) return@addOnMapClickListener true

                                    false
                                }

                                val newZoneFills = mutableListOf<Fill>()
                                val newZoneLines = mutableListOf<Line>()
                                val newFillToZone = mutableMapOf<Long, Zone>()

                                for (zone in zones) {
                                    try {
                                        val rings = wktToFillLatLngs(zone.geometryWkt)
                                        if (rings.isEmpty()) continue

                                        val fillOptions = FillOptions()
                                            .withLatLngs(rings)
                                            .withFillColor("#4D2E7D32")
                                            .withFillOpacity(0.3f)
                                        val fill = fillManager!!.create(fillOptions)
                                        newZoneFills.add(fill)
                                        newFillToZone[fill.id] = zone

                                        val outerRing = rings.first()
                                        val lineOptions = LineOptions()
                                            .withLatLngs(outerRing)
                                            .withLineColor("#FF1B5E20")
                                            .withLineWidth(2f)
                                        val line = lineManager!!.create(lineOptions)
                                        newZoneLines.add(line)
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
                                }

                                zoneFills = newZoneFills
                                zoneLines = newZoneLines
                                fillToZoneMap = newFillToZone

                                symbolManager?.addClickListener { symbol ->
                                    val successState = uiState as? MainUiState.Success
                                    for (poi in pois) {
                                        if (poi.latitude == symbol.latLng?.latitude &&
                                            poi.longitude == symbol.latLng?.longitude
                                        ) {
                                            zoneDetailViewModel.selectPoi(
                                                poi,
                                                successState?.latitude,
                                                successState?.longitude
                                            )
                                            return@addClickListener true
                                        }
                                    }
                                    false
                                }
                            }
                        }

                        mapViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    val state = uiState
                    if (state is MainUiState.Success) {
                        val lat = state.latitude
                        val lon = state.longitude
                        if (lat != null && lon != null) {
                            val sm = symbolManager ?: return@AndroidView
                            val current = userSymbol
                            if (current != null) {
                                current.latLng = LatLng(lat, lon)
                                current.iconRotate = lastAzimuth
                                sm.update(current)
                            } else {
                                val options = SymbolOptions()
                                    .withLatLng(LatLng(lat, lon))
                                    .withIconImage("user-arrow")
                                    .withIconRotate(lastAzimuth)
                                val symbol = sm.create(options)
                                userSymbol = symbol
                            }
                        } else {
                            val current = userSymbol
                            if (current != null) {
                                symbolManager?.delete(current)
                                userSymbol = null
                            }
                        }
                    }
                },
                onRelease = { mapView ->
                    mapboxMapInstance?.let { map ->
                        val pos = map.cameraPosition
                        mapViewModel.saveMapState(pos.target?.latitude, pos.target?.longitude, pos.zoom)
                    }
                    mapView.onDestroy()
                }
            )

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

                        FloatingActionButton(
                            onClick = { isSettingsOpen = !isSettingsOpen },
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
                    }

                    AnimatedVisibility(
                        visible = isSettingsOpen,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 })
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.width(220.dp)
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
                                            val mv = mapViewInstance
                                            if (map != null && mv != null) {
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

            val debugError by viewModel.debugError.collectAsState()
            debugError?.let { errorMsg ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearDebugError() },
                    title = { Text("Błąd Debugowania (Crash Log)") },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = errorMsg,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearDebugError() }) {
                            Text("Zamknij")
                        }
                    }
                )
            }
        }
    }
}

private fun registerPoiIcons(ctx: Context, style: Style) {
    val shelterDrawable = androidx.core.content.ContextCompat.getDrawable(ctx, com.indiana.zwl.R.drawable.ic_shelter)
    shelterDrawable?.let { drawable ->
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.parseColor("#4E342E"))
        val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        style.addImage("poi-shelter", bitmap)
    }

    val fireplaceDrawable = androidx.core.content.ContextCompat.getDrawable(ctx, com.indiana.zwl.R.drawable.ic_fireplace)
    fireplaceDrawable?.let { drawable ->
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.parseColor("#E65100"))
        val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        style.addImage("poi-fireplace", bitmap)
    }

    val genericDrawable = androidx.core.content.ContextCompat.getDrawable(ctx, com.indiana.zwl.R.drawable.ic_generic_point)
    genericDrawable?.let { drawable ->
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.parseColor("#1976D2"))
        val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        style.addImage("poi-generic", bitmap)
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        val km = meters / 1000.0
        String.format(java.util.Locale.US, "%.1f km", km)
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
