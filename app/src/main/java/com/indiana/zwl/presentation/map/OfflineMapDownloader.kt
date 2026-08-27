package com.indiana.zwl.presentation.map

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object OfflineMapDownloader {

    private const val DOWNLOAD_CONCURRENCY = 4
    private const val DB_FILE_NAME = "map.mbtiles"

    private sealed class TileResult {
        object Skipped : TileResult()
        object Downloaded : TileResult()
        object NotDownloaded : TileResult()
        object NetworkError : TileResult()
    }

    fun getTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    fun getTileY(lat: Double, zoom: Int): Int {
        val latRad = lat * Math.PI / 180.0
        val latRadBounded = maxOf(-1.484, minOf(1.484, latRad))
        val y = (1.0 - ln(kotlin.math.tan(latRadBounded) + 1.0 / kotlin.math.cos(latRadBounded)) / Math.PI) / 2.0 * (1 shl zoom)
        return y.toInt()
    }

    private fun tileUrl(x: Int, y: Int, z: Int): String {
        val hosts = arrayOf("a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org")
        val host = hosts[((x xor y) and Int.MAX_VALUE) % hosts.size]
        return "https://$host/$z/$x/$y.png"
    }

    suspend fun downloadArea(
        latSouth: Double, latNorth: Double,
        lonWest: Double, lonEast: Double,
        cacheDir: File,
        client: okhttp3.OkHttpClient,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
            val zoomLevels = 10..16
            val tiles = mutableListOf<Triple<Int, Int, Int>>()
            for (z in zoomLevels) {
                val startX = getTileX(lonWest, z)
                val endX = getTileX(lonEast, z)
                val y1 = getTileY(latNorth, z)
                val y2 = getTileY(latSouth, z)
                val startY = minOf(y1, y2)
                val endY = maxOf(y1, y2)
                val minX = minOf(startX, endX)
                val maxX = maxOf(startX, endX)
                for (x in minX..maxX) {
                    for (y in startY..endY) {
                        tiles.add(Triple(x, y, z))
                    }
                }
            }

            val total = tiles.size
            if (total == 0) {
                onError("Obszar nie zawiera żadnych kafelków.")
                return@withContext
            }

            if (total > 500) {
                onError("Obszar jest zbyt duży! Przybliż mapę, aby pobrać mniejszy wycinek (maksymalnie 500 kafelków, aktualnie: $total).")
                return@withContext
            }

            val dbFile = File(cacheDir, DB_FILE_NAME)
            dbFile.parentFile?.mkdirs()
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tiles (" +
                        "zoom_level INTEGER NOT NULL, " +
                        "tile_column INTEGER NOT NULL, " +
                        "tile_row INTEGER NOT NULL, " +
                        "tile_data BLOB NOT NULL)"
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT NOT NULL, value TEXT NOT NULL)")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('format', 'png')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('name', 'Legalny Bushcraft offline')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('type', 'basemap')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('version', '1.1')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('minzoom', '10')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('maxzoom', '16')")
                db.execSQL("INSERT OR REPLACE INTO metadata (name, value) VALUES ('bounds', '$lonWest,$latSouth,$lonEast,$latNorth')")

                val existingTiles = HashSet<Triple<Int, Int, Int>>()
                db.query("tiles", arrayOf("zoom_level", "tile_column", "tile_row"), null, null, null, null, null).use { c ->
                    while (c.moveToNext()) {
                        existingTiles.add(Triple(c.getInt(0), c.getInt(1), c.getInt(2)))
                    }
                }

                onProgress(0f, "Rozpoczynanie pobierania...")

                val writeMutex = Mutex()
                var successCount = 0
                for (batchStart in 0 until total step DOWNLOAD_CONCURRENCY) {
                    val batchEnd = minOf(batchStart + DOWNLOAD_CONCURRENCY, total)
                    val results = coroutineScope {
                        (batchStart until batchEnd).map { index ->
                            async(Dispatchers.IO) {
                                downloadTile(tiles[index], existingTiles, db, writeMutex, client)
                            }
                        }.awaitAll()
                    }

                    var batchFailed = false
                    for ((batchOffset, result) in results.withIndex()) {
                        val index = batchStart + batchOffset
                        when (result) {
                            TileResult.Skipped -> {
                                successCount++
                                onProgress((index + 1).toFloat() / total, "Pomiń istniejący: ${index + 1} z $total...")
                            }
                            TileResult.Downloaded -> {
                                successCount++
                                onProgress((index + 1).toFloat() / total, "Pobieranie: ${index + 1} z $total...")
                            }
                            TileResult.NotDownloaded -> {
                                onProgress((index + 1).toFloat() / total, "Pobieranie: ${index + 1} z $total...")
                            }
                            TileResult.NetworkError -> {
                                batchFailed = true
                            }
                        }
                    }

                    if (batchFailed) {
                        onError("Błąd połączenia sieciowego podczas pobierania. Przerywam.")
                        return@withContext
                    }
                }

                onSuccess(successCount)
            } finally {
                db.close()
            }
        }

    private suspend fun downloadTile(
        tile: Triple<Int, Int, Int>,
        existingTiles: HashSet<Triple<Int, Int, Int>>,
        db: SQLiteDatabase,
        writeMutex: Mutex,
        client: okhttp3.OkHttpClient
    ): TileResult {
        val (x, y, z) = tile
        if (!existingTiles.add(tile)) {
            return TileResult.Skipped
        }

        val url = tileUrl(x, y, z)
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "LegalnyBushcraft/1.0 (Android)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        writeMutex.withLock {
                            val values = ContentValues().apply {
                                put("zoom_level", z)
                                put("tile_column", x)
                                put("tile_row", (1 shl z) - 1 - y)
                                put("tile_data", bytes)
                            }
                            db.insertWithOnConflict(
                                "tiles", null, values,
                                SQLiteDatabase.CONFLICT_IGNORE
                            )
                        }
                        TileResult.Downloaded
                    } else {
                        TileResult.NotDownloaded
                    }
                } else {
                    TileResult.NotDownloaded
                }
            }
        } catch (e: IOException) {
            TileResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            TileResult.NotDownloaded
        }
    }
}

private fun ln(x: Double): Double = kotlin.math.ln(x)