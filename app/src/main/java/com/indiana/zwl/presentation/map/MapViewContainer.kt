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
import com.indiana.zwl.data.local.ZoneEntity
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
    zones: List<ZoneEntity>,
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
            downloadArea(
                context = context,
                mapView = mapViewInstance!!,
                tileCache = tileCacheInstance!!,
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
                        "Pobrano automatycznie $success z $total kafelków do cache offline!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onMessage = { msg ->
                    isDownloadingArea = false
                }
            )
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

                    val tileSource = createOnlineTileSource()

                    val downloadLayer = TileDownloadLayer(
                        tileCache,
                        this.model.mapViewPosition,
                        tileSource,
                        AndroidGraphicFactory.INSTANCE
                    )
                    this.layerManager.layers.add(downloadLayer)

                    this.setCenter(LatLong(52.23, 21.01))
                    this.setZoomLevel(15)

                    drawZonePolygons(ctx, this, zones)

                    // Initialize user location marker
                    val userLocBitmap = createUserLocationBitmap(ctx)
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
                        marker.requestRedraw()
                    }
                    if (!hasCenteredOnStartup) {
                        mapView.setCenter(userPos)
                        mapView.setZoomLevel(15)
                        hasCenteredOnStartup = true
                    }
                }
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
                            downloadArea(
                                context = context,
                                mapView = mv,
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

private fun getTileX(lon: Double, zoom: Int): Int {
    return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

private fun getTileY(lat: Double, zoom: Int): Int {
    val latRad = lat * Math.PI / 180.0
    val latRadBounded = maxOf(-1.484, minOf(1.484, latRad))
    val y = (1.0 - ln(tan(latRadBounded) + 1.0 / cos(latRadBounded)) / Math.PI) / 2.0 * (1 shl zoom)
    return y.toInt()
}

private suspend fun downloadArea(
    context: Context,
    mapView: MapView,
    tileCache: TileCache,
    onStart: (total: Int) -> Unit,
    onProgress: (progress: Float, text: String) -> Unit,
    onFinished: (successCount: Int, total: Int) -> Unit,
    onMessage: (msg: String) -> Unit
) {
    val bbox = mapView.boundingBox
    if (bbox == null) {
        onMessage("Błąd: Nie można określić widocznego obszaru mapy.")
        return
    }

    val zoomLevels = 10..16
    val tiles = mutableListOf<Tile>()
    for (z in zoomLevels) {
        val startX = getTileX(bbox.minLongitude, z)
        val endX = getTileX(bbox.maxLongitude, z)
        val y1 = getTileY(bbox.maxLatitude, z)
        val y2 = getTileY(bbox.minLatitude, z)
        val startY = minOf(y1, y2)
        val endY = maxOf(y1, y2)

        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)

        for (x in minX..maxX) {
            for (y in startY..endY) {
                tiles.add(Tile(x, y, z.toByte(), 256))
            }
        }
    }

    val total = tiles.size
    if (total == 0) {
        onMessage("Obszar nie zawiera żadnych kafelków.")
        return
    }

    if (total > 500) {
        onMessage("Obszar jest zbyt duży! Przybliż mapę, aby pobrać mniejszy wycinek (maksymalnie 500 kafelków, aktualnie: $total).")
        return
    }

    onStart(total)

    val tileSource = createOnlineTileSource()
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    withContext(Dispatchers.IO) {
        var successCount = 0
        for ((index, tile) in tiles.withIndex()) {
            val job = DownloadJob(tile, tileSource)

            if (tileCache.containsKey(job)) {
                successCount++
                withContext(Dispatchers.Main) {
                    onProgress((index + 1).toFloat() / total, "Pomiń istniejący: ${index + 1} z $total...")
                }
                continue
            }

            val url = tileSource.getTileUrl(tile)
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "ZanocujWLesieLokator/1.0 (Android)")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val inputStream = java.io.ByteArrayInputStream(bytes)
                        val bitmap = AndroidGraphicFactory.INSTANCE.createTileBitmap(
                            inputStream,
                            256,
                            false
                        )
                        tileCache.put(job, bitmap)
                        successCount++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                onProgress((index + 1).toFloat() / total, "Pobieranie: ${index + 1} z $total...")
            }
        }

        withContext(Dispatchers.Main) {
            onFinished(successCount, total)
        }
    }
}

private fun drawZonePolygons(context: Context, mapView: MapView, zones: List<ZoneEntity>) {
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

private fun createUserLocationBitmap(context: Context): org.mapsforge.core.graphics.Bitmap {
    val size = (16f * context.resources.displayMetrics.density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#007AFF")
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
    }
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth, paint)
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth, borderPaint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, androidBitmap)
    return AndroidGraphicFactory.convertToBitmap(drawable)
}

private fun createOnlineTileSource(): OnlineTileSource {
    val hostNames = arrayOf("a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org")
    val tileSource = object : OnlineTileSource(hostNames, 19) {
        override fun getTileUrl(tile: Tile): URL {
            val host = hostNames[((tile.tileX xor tile.tileY) and Int.MAX_VALUE) % hostNames.size]
            return URL("https://$host/${tile.zoomLevel}/${tile.tileX}/${tile.tileY}.png")
        }
    }
    tileSource.setUserAgent("ZanocujWLesieLokator/1.0 (Android)")
    return tileSource
}
