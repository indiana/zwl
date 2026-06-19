package com.example.zwl.presentation.map

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.zwl.data.local.ZoneEntity
import com.example.zwl.presentation.MainUiState
import com.example.zwl.presentation.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.locationtech.jts.io.WKTReader
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polygon
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File
import java.io.FileOutputStream

@Composable
fun MapViewContainer(
    viewModel: MainViewModel,
    zones: List<ZoneEntity>,
    onCloseMap: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mapFileExists by remember { mutableStateOf(false) }
    var isLoadingMap by remember { mutableStateOf(false) }
    val cacheFile = File(context.cacheDir, "offline.map")

    LaunchedEffect(Unit) {
        mapFileExists = cacheFile.exists() && cacheFile.length() > 0
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingMap = true
            coroutineScope.launch {
                val success = copyUriToCache(context, uri, cacheFile)
                isLoadingMap = false
                mapFileExists = success
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!mapFileExists) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Brak Pliku Mapy Offline",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aplikacja działa w trybie offline i wymaga wektorowego pliku mapy w formacie Mapsforge (.map).\n\nPobierz mapę (np. dla Polski ze strony OpenAndroMaps lub Mapsforge) i wybierz ją poniżej.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (isLoadingMap) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                } else {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Wybierz plik mapy (.map)", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onCloseMap) {
                    Text("Wróć do ekranu głównego", color = Color(0xFF81C784))
                }
            }
        } else {
            var userMarker by remember { mutableStateOf<Marker?>(null) }
            val uiState by viewModel.uiState.collectAsState()

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

                        try {
                            val mapDataStore = MapFile(cacheFile)
                            this.mapZoomControls.setZoomLevelMin(mapDataStore.startPositionAndZoomLimit.zoomLimitMin)
                            this.mapZoomControls.setZoomLevelMax(mapDataStore.startPositionAndZoomLimit.zoomLimitMax)

                            val tileRendererLayer = TileRendererLayer(
                                tileCache,
                                mapDataStore,
                                this.model.mapViewPosition,
                                AndroidGraphicFactory.INSTANCE
                            )
                            tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)

                            this.layerManager.layers.add(tileRendererLayer)

                            val startPos = mapDataStore.startPositionAndZoomLimit.startPosition
                            this.setCenter(startPos ?: LatLong(52.23, 21.01))
                            this.setZoomLevel(12)

                            drawZonePolygons(ctx, this, zones)

                            // Initialize user location marker
                            val userLocBitmap = createUserLocationBitmap(ctx)
                            val marker = Marker(LatLong(52.23, 21.01), userLocBitmap, 0, 0)
                            this.layerManager.layers.add(marker)
                            userMarker = marker

                        } catch (e: Exception) {
                            cacheFile.delete()
                            mapFileExists = false
                        }
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
                        // Center map to user position dynamically on updates
                        mapView.setCenter(userPos)
                    }
                }
            )

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
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Wróć do statusu", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private suspend fun copyUriToCache(context: Context, uri: Uri, destFile: File): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

private fun drawZonePolygons(context: Context, mapView: MapView, zones: List<ZoneEntity>) {
    val graphicFactory = AndroidGraphicFactory.INSTANCE
    val wktReader = WKTReader()

    val fillPaint = graphicFactory.createPaint().apply {
        color = graphicFactory.createColor(0x4D, 0x2E, 0x7D, 0x32) // 30% alpha green
        setStyle(Style.FILL)
    }

    val strokePaint = graphicFactory.createPaint().apply {
        color = graphicFactory.createColor(0xFF, 0x1B, 0x5E, 0x20) // dark green
        setStyle(Style.STROKE)
        strokeWidth = AndroidUtil.dpToPixel(context, 2f)
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
    val size = AndroidUtil.dpToPixel(context, 16f).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#007AFF") // Apple blue
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = AndroidUtil.dpToPixel(context, 2f)
    }
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth, paint)
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth, borderPaint)

    return AndroidGraphicFactory.convertToBitmap(bitmap)
}
