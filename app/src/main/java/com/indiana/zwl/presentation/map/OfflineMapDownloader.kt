package com.indiana.zwl.presentation.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.DownloadJob
import org.mapsforge.map.layer.download.tilesource.OnlineTileSource
import java.io.IOException
import java.net.URL
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos

sealed class DownloadStatus {
    data class Start(val total: Int) : DownloadStatus()
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Finished(val successCount: Int, val total: Int) : DownloadStatus()
    data class Message(val msg: String) : DownloadStatus()
}

object OfflineMapDownloader {

    fun getTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    fun getTileY(lat: Double, zoom: Int): Int {
        val latRad = lat * Math.PI / 180.0
        val latRadBounded = maxOf(-1.484, minOf(1.484, latRad))
        val y = (1.0 - ln(tan(latRadBounded) + 1.0 / cos(latRadBounded)) / Math.PI) / 2.0 * (1 shl zoom)
        return y.toInt()
    }

    fun createOnlineTileSource(): OnlineTileSource {
        val hostNames = arrayOf("a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org")
        val tileSource = object : OnlineTileSource(hostNames, 19) {
            override fun getTileUrl(tile: Tile): URL {
                val host = hostNames[((tile.tileX xor tile.tileY) and Int.MAX_VALUE) % hostNames.size]
                return URL("https://$host/${tile.zoomLevel}/${tile.tileX}/${tile.tileY}.png")
            }
        }
        tileSource.setUserAgent("LegalnyBushcraft/1.0 (Android)")
        return tileSource
    }

    fun downloadArea(
        bbox: BoundingBox,
        tileSize: Int,
        tileCache: TileCache,
        client: okhttp3.OkHttpClient
    ): Flow<DownloadStatus> = flow {
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
                    tiles.add(Tile(x, y, z.toByte(), tileSize))
                }
            }
        }

        val total = tiles.size
        if (total == 0) {
            emit(DownloadStatus.Message("Obszar nie zawiera żadnych kafelków."))
            return@flow
        }

        if (total > 500) {
            emit(DownloadStatus.Message("Obszar jest zbyt duży! Przybliż mapę, aby pobrać mniejszy wycinek (maksymalnie 500 kafelków, aktualnie: $total)."))
            return@flow
        }

        emit(DownloadStatus.Start(total))

        val tileSource = createOnlineTileSource()

        var successCount = 0
        for ((index, tile) in tiles.withIndex()) {
            val job = DownloadJob(tile, tileSource)

            if (tileCache.containsKey(job)) {
                successCount++
                emit(DownloadStatus.Progress((index + 1).toFloat() / total, "Pomiń istniejący: ${index + 1} z $total..."))
                continue
            }

            val url = tileSource.getTileUrl(tile)
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "LegalnyBushcraft/1.0 (Android)")
                .build()

            try {
                // Execute network call on Dispatchers.IO
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val inputStream = java.io.ByteArrayInputStream(bytes)
                        val bitmap = AndroidGraphicFactory.INSTANCE.createTileBitmap(
                            inputStream,
                            tileSize,
                            false
                        )
                        tileCache.put(job, bitmap)
                        successCount++
                    }
                }
            } catch (e: IOException) {
                emit(DownloadStatus.Message("Błąd połączenia sieciowego podczas pobierania. Przerywam."))
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }

            emit(DownloadStatus.Progress((index + 1).toFloat() / total, "Pobieranie: ${index + 1} z $total..."))
        }

        emit(DownloadStatus.Finished(successCount, total))
    }
}
