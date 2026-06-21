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
import com.indiana.zwl.presentation.MainUiState
import com.indiana.zwl.presentation.MainViewModel
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
import org.mapsforge.map.layer.overlay.Polygon
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos

@Composable
fun MapViewContainer(
    viewModel: MainViewModel,
    zones: List<Zone>,
    onCloseMap: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var tileCacheInstance by remember { mutableStateOf<TileCache?>(null) }
    var hasCenteredOnStartup by remember { mutableStateOf(false) }

    // Download state
    var isDownloadingArea by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadText by remember { mutableStateOf("") }

    var userMarker by remember { mutableStateOf<Marker?>(null) }

    LaunchedEffect(hasCenteredOnStartup, mapViewInstance, tileCacheInstance) {
        if (hasCenteredOnStartup && mapViewInstance != null && tileCacheInstance != null && !isDownloadingArea) {
            try {
                // Wait for map layout to complete and boundingBox to be populated
                var bbox = mapViewInstance!!.boundingBox
                while (bbox == null || bbox.latitudeSpan == 0.0 || bbox.longitudeSpan == 0.0) {
                    kotlinx.coroutines.delay(100)
                    bbox = mapViewInstance!!.boundingBox
                }
                OfflineMapDownloader.downloadArea(
                    bbox = bbox,
                    tileSize = mapViewInstance!!.model.displayModel.tileSize,
                    tileCache = tileCacheInstance!!,
                    onStart = {
                        isDownloadingArea = true
                        downloadProgress = 0f
                        downloadText = "Automatyczne pobieranie..."
                    },
                    onProgress = { progress, text ->
                        downloadProgress = progress
                        downloadText = text
                    },
                    onFinished = { success, total ->
                        Toast.makeText(
                            context,
                            "Pobrano automatycznie $success z $total kafelków do cache offline!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onMessage = { msg -> }
                )
            } finally {
                isDownloadingArea = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                        this.model.frameBufferModel.overdrawFactor
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

                    this.setCenter(LatLong(52.23, 21.01))
                    this.setZoomLevel(15)

                    drawZonePolygons(ctx, this, zones)

                    // Initialize user location marker
                    val userLocBitmap = getUserLocationArrowBitmap(ctx, 0f)
                    val marker = Marker(LatLong(52.23, 21.01), userLocBitmap, 0, 0)
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
                        marker.setBitmap(getUserLocationArrowBitmap(mapView.context, state.azimuth))
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
            }
        )

        // Floating download button (top-right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Button(
                onClick = {
                    val mv = mapViewInstance
                    val tc = tileCacheInstance
                    if (mv != null && tc != null) {
                        coroutineScope.launch {
                            val bbox = mv.boundingBox
                            if (bbox != null) {
                                OfflineMapDownloader.downloadArea(
                                    bbox = bbox,
                                    tileSize = mv.model.displayModel.tileSize,
                                    tileCache = tc,
                                    onStart = {
                                        isDownloadingArea = true
                                        downloadProgress = 0f
                                        downloadText = "Rozpoczynanie pobierania..."
                                    },
                                    onProgress = { progress, text ->
                                        downloadProgress = progress
                                        downloadText = text
                                    },
                                    onFinished = { success, total ->
                                        isDownloadingArea = false
                                        Toast.makeText(
                                            context,
                                            "Pobrano pomyślnie $success z $total kafelków do cache offline!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onMessage = { msg ->
                                        isDownloadingArea = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Brak widocznego obszaru", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Mapa nie jest gotowa.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text("Pobierz obszar offline", fontWeight = FontWeight.Bold, color = Color.White)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
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
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}


private fun drawZonePolygons(context: Context, mapView: MapView, zones: List<Zone>) {
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

                    val polygonOverlay = Polygon(fillPaint, strokePaint, graphicFactory)
                    polygonOverlay.setPoints(mfPoints)
                    mapView.layerManager.layers.add(polygonOverlay)
                }
            }
        } catch (e: Exception) {
            // Ignoruj błędne strefy
        }
    }
}

private var userLocationArrowBitmaps: Array<org.mapsforge.core.graphics.Bitmap>? = null

private fun getUserLocationArrowBitmap(context: Context, azimuth: Float): org.mapsforge.core.graphics.Bitmap {
    if (userLocationArrowBitmaps == null) {
        userLocationArrowBitmaps = Array(360) { i ->
            val bmp = createUserLocationArrowBitmap(context, i.toFloat())
            bmp.incrementRefCount()
            bmp
        }
    }
    var index = Math.round(azimuth).toInt() % 360
    if (index < 0) index += 360
    return userLocationArrowBitmaps!![index]
}

private fun createUserLocationArrowBitmap(context: Context, rotationDegrees: Float): org.mapsforge.core.graphics.Bitmap {
    val size = (32f * context.resources.displayMetrics.density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)
    
    val fillPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#007AFF")
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    val radius = size / 2f
    canvas.rotate(rotationDegrees, radius, radius)

    val path = android.graphics.Path().apply {
        moveTo(radius, size * 0.1f) // Top tip
        lineTo(size * 0.85f, size * 0.85f) // Bottom right
        lineTo(radius, size * 0.65f) // Bottom center indent
        lineTo(size * 0.15f, size * 0.85f) // Bottom left
        close()
    }

    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, borderPaint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, androidBitmap)
    return AndroidGraphicFactory.convertToBitmap(drawable)
}

