package com.indiana.zwl.presentation.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.presentation.MainUiState
import com.indiana.zwl.presentation.MainViewModel
import com.indiana.zwl.presentation.DownloadEvent
import com.indiana.zwl.presentation.SelectedZoneDetails
import com.indiana.zwl.presentation.theme.ZwlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.locationtech.jts.io.WKTReader
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.DownloadJob
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource
import org.mapsforge.map.layer.overlay.Marker
import java.net.URL
import java.io.File
import org.mapsforge.map.layer.overlay.Polygon
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.indiana.zwl.presentation.map.util.isOnline
import com.indiana.zwl.presentation.map.util.rememberIsOnline
import com.indiana.zwl.presentation.map.util.createUserLocationArrowBitmap
import com.indiana.zwl.presentation.map.util.cleanupCorruptedCacheFiles
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

@Composable
fun MapViewContainer(
    viewModel: MainViewModel,
    zones: List<Zone>,
    onCloseMap: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val selectedZone by viewModel.selectedZoneDetails.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedZone()
        }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var tileCacheInstance by remember { mutableStateOf<TileCache?>(null) }
    var hasCenteredOnStartup by remember { mutableStateOf(false) }

    // Download state from ViewModel
    val isDownloadingArea by viewModel.isDownloadingArea.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadText by viewModel.downloadText.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.downloadEvent.collect { event ->
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

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            cleanupCorruptedCacheFiles(File(context.externalCacheDir, "mapcache"))
        }
    }



    var userMarker by remember { mutableStateOf<RotatingMarker?>(null) }

    val isOnlineState by rememberIsOnline()

    // Automatic download on startup removed to prevent concurrent download/write database lock conflicts with TileDownloadLayer.

    val isInZone = (uiState as? MainUiState.Success)?.locationStatus is LocationStatus.InZone

    ZwlTheme(isInZone = isInZone) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    isClickable = true
                    getMapScaleBar().isVisible = true
                    setBuiltInZoomControls(true)

                    val tileCache = AndroidUtil.createTileCache(
                        ctx,
                        "mapcache",
                        this.model.displayModel.tileSize,
                        1f,
                        this.model.frameBufferModel.overdrawFactor,
                        true
                    )
                    tileCacheInstance = tileCache

                    this.mapZoomControls.setZoomLevelMin(8)
                    this.mapZoomControls.setZoomLevelMax(20)

                    val tileSource = OfflineMapDownloader.createOnlineTileSource()

                    val downloadLayer = TileDownloadLayer(
                        tileCache,
                        this.model.mapViewPosition,
                        tileSource,
                        AndroidGraphicFactory.INSTANCE
                    )
                    this.layerManager.layers.add(downloadLayer)
                    downloadLayer.onResume()

                    // Add background tap interceptor to clear selection when tapping empty areas of the map
                    this.layerManager.layers.add(MapTapInterceptor {
                        viewModel.clearSelectedZone()
                    })

                    this.setCenter(LatLong(52.23, 21.01))
                    this.setZoomLevel(15)

                    drawZonePolygons(
                        context = ctx,
                        mapView = this,
                        zones = zones,
                        onZoneClick = { zone, geom, latLong ->
                            viewModel.selectZone(zone, geom, latLong.latitude, latLong.longitude)
                        },
                        onZoneError = { errorMsg ->
                            viewModel.setDebugError(errorMsg)
                        }
                    )

                    // Initialize user location marker
                    val userLocBitmap = createUserLocationArrowBitmap(ctx)
                    val marker = RotatingMarker(LatLong(52.23, 21.01), userLocBitmap, 0, 0)
                    this.layerManager.layers.add(marker)
                    userMarker = marker

                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                val state = uiState
                if (state is MainUiState.Success) {
                    val userPos = LatLong(state.latitude, state.longitude)
                    userMarker?.let { marker ->
                        marker.latLong = userPos
                        marker.azimuth = state.azimuth
                        marker.requestRedraw()
                    }
                    if (!hasCenteredOnStartup) {
                        mapView.setCenter(userPos)
                        mapView.setZoomLevel(15)
                        hasCenteredOnStartup = true
                    }
                }
            },
            onRelease = { mapView ->
                mapView.destroyAll()
                tileCacheInstance?.destroy()
                tileCacheInstance = null
            }
        )

        // Top-right UI controls (Download button / Offline status badge)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            AnimatedVisibility(
                visible = isOnlineState,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        if (!isOnline(context)) {
                            Toast.makeText(context, "Jesteś w trybie offline", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val mv = mapViewInstance
                        val tc = tileCacheInstance
                        if (mv != null && tc != null) {
                            val bbox = mv.boundingBox
                            if (bbox != null) {
                                viewModel.downloadMapArea(
                                    bbox = bbox,
                                    tileSize = mv.model.displayModel.tileSize,
                                    tileCache = tc
                                )
                            } else {
                                Toast.makeText(context, "Brak widocznego obszaru", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Mapa nie jest gotowa.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text("Pobierz obszar offline", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

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
        }

        // Return button (bottom center)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = onCloseMap,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(48.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Wróć do statusu", fontWeight = FontWeight.Bold)
            }
        }

        // Silent background download card in the top-left
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
                            progress = downloadProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedZone != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { height -> height / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { height -> height / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedZone?.let { details ->
                    ZoneDetailsCard(
                        details = details,
                        onClose = { viewModel.clearSelectedZone() },
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
}


private fun drawZonePolygons(
    context: Context,
    mapView: MapView,
    zones: List<Zone>,
    onZoneClick: (Zone, org.locationtech.jts.geom.Polygon, LatLong) -> Unit,
    onZoneError: (String) -> Unit
) {
    val graphicFactory = AndroidGraphicFactory.INSTANCE
    val wktReader = WKTReader()

    val fillPaint = graphicFactory.createPaint().apply {
        color = graphicFactory.createColor(0x4D, 0x2E, 0x7D, 0x32)
        setStyle(Style.FILL)
    }

    val strokePaint = graphicFactory.createPaint().apply {
        color = graphicFactory.createColor(0xFF, 0x1B, 0x5E, 0x20)
        setStyle(Style.STROKE)
        strokeWidth = 2f * context.resources.displayMetrics.density
    }

    for (zone in zones) {
        try {
            val geom = wktReader.read(zone.geometryWkt)
            val numGeoms = geom.numGeometries
            for (g in 0 until numGeoms) {
                val subGeom = geom.getGeometryN(g)
                if (subGeom is org.locationtech.jts.geom.Polygon) {
                    val shell = subGeom.exteriorRing
                    val mfPoints = ArrayList<LatLong>()
                    for (c in shell.coordinates) {
                        mfPoints.add(LatLong(c.y, c.x))
                    }

                    val clickablePolygon = ClickablePolygon(
                        mapView = mapView,
                        zone = zone,
                        jtsPolygon = subGeom,
                        fillPaint = fillPaint,
                        strokePaint = strokePaint,
                        graphicFactory = graphicFactory,
                        onClick = onZoneClick,
                        onError = onZoneError
                    )
                    clickablePolygon.setPoints(mfPoints)
                    mapView.layerManager.layers.add(clickablePolygon)
                }
            }
        } catch (e: Throwable) {
            onZoneError("drawZonePolygons error:\n" + e.stackTraceToString())
        }
    }
}

class ClickablePolygon(
    private val mapView: MapView,
    private val zone: Zone,
    private val jtsPolygon: org.locationtech.jts.geom.Polygon,
    fillPaint: org.mapsforge.core.graphics.Paint,
    strokePaint: org.mapsforge.core.graphics.Paint,
    graphicFactory: org.mapsforge.core.graphics.GraphicFactory,
    private val onClick: (Zone, org.locationtech.jts.geom.Polygon, LatLong) -> Unit,
    private val onError: (String) -> Unit
) : org.mapsforge.map.layer.overlay.Polygon(fillPaint, strokePaint, graphicFactory) {
    override fun onTap(tapLatLong: LatLong?, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point?): Boolean {
        try {
            if (tapLatLong == null) {
                onError("Tap details: tapLatLong is null")
                return false
            }
            val gf = org.locationtech.jts.geom.GeometryFactory()
            val clickedPoint = gf.createPoint(org.locationtech.jts.geom.Coordinate(tapLatLong.longitude, tapLatLong.latitude))
            val contains = jtsPolygon.contains(clickedPoint)
            
            // Temporary debug alert
            onError("DEBUG TAP:\nZone: ${zone.forestDistrict}\nClicked: (${tapLatLong.latitude}, ${tapLatLong.longitude})\nContains: $contains\nEnvelope: ${jtsPolygon.envelopeInternal}")

            if (contains) {
                mapView.post {
                    try {
                        onClick(zone, jtsPolygon, tapLatLong)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        onError("onClick posted execution error:\n" + e.stackTraceToString())
                    }
                }
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            onError("ClickablePolygon.onTap crash:\n" + e.stackTraceToString())
        }
        return super.onTap(tapLatLong, layerXY, tapXY)
    }
}

class MapTapInterceptor(private val onMapTap: () -> Unit) : org.mapsforge.map.layer.Layer() {
    override fun draw(
        boundingBox: org.mapsforge.core.model.BoundingBox?,
        zoomLevel: Byte,
        canvas: org.mapsforge.core.graphics.Canvas?,
        topLeftPoint: org.mapsforge.core.model.Point?,
        rotation: org.mapsforge.core.model.Rotation?
    ) {
        // Nothing to draw
    }

    override fun onTap(tapLatLong: LatLong?, layerXY: org.mapsforge.core.model.Point?, tapXY: org.mapsforge.core.model.Point?): Boolean {
        onMapTap()
        return false
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
fun ZoneDetailsCard(
    details: SelectedZoneDetails,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = details.zone.forestDistrict,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Strefa Zanocuj w Lesie",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "ODLEGŁOŚĆ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = details.distanceMeters?.let { formatDistance(it) } ?: "Obliczanie...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "ZAGROŻENIE POŻAROWE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (details.isLoadingFireRisk) {
                            Box(modifier = Modifier.size(16.dp)) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val riskText = when (details.fireRiskLevel) {
                                0 -> "STOPIEŃ 0 (Brak)"
                                1 -> "STOPIEŃ 1 (Niskie)"
                                2 -> "STOPIEŃ 2 (Średnie)"
                                3 -> "STOPIEŃ 3 (WYSOKIE)"
                                else -> "Nieznany (brak sieci)"
                            }
                            val riskColor = when (details.fireRiskLevel) {
                                0 -> Color(0xFF81C784)
                                1 -> Color(0xFFFFF176)
                                2 -> Color(0xFFFFB74D)
                                3 -> Color(0xFFE57373)
                                else -> Color(0xFFB0BEC5)
                            }
                            Text(
                                text = riskText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "UŻYWANIE KUCHENEK GAZOWYCH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (details.isLoadingFireRisk) {
                        Text(
                            text = "Pobieranie zasad...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val permissionText = when (details.fireRiskLevel) {
                            0, 1, 2 -> "DOZWOLONE"
                            3 -> "BEZWZGLĘDNY ZAKAZ"
                            else -> "WARUNKOWO DOZWOLONE (brak danych)"
                        }
                        val permissionColor = when (details.fireRiskLevel) {
                            0, 1, 2 -> Color(0xFF81C784)
                            3 -> Color(0xFFEF5350)
                            else -> Color(0xFFFFF176)
                        }
                        Text(
                            text = permissionText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = permissionColor
                        )
                    }
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
