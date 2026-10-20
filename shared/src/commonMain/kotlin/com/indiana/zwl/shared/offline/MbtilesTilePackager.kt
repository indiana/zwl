package com.indiana.zwl.shared.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class Region(
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double
)

sealed interface TileFetchResult {
    data class Ok(val bytes: ByteArray) : TileFetchResult
    object Empty : TileFetchResult
    object NetworkError : TileFetchResult
}

interface TileFetcher {
    suspend fun fetch(x: Int, y: Int, z: Int): TileFetchResult
}

data class TileRef(val z: Int, val x: Int, val y: Int)

/**
 * Platform-neutral storage for an MBTiles database.
 *
 * Implementations own the on-disk file and the SQL schema; the packer owns all
 * the MBTiles-specific logic (zoom levels, TMS y-flip, cap, metadata, batching).
 */
interface MbtilesStore {
    /** Creates/opens the database and writes the base metadata. Call before anything else. */
    fun open(bounds: String, minZoom: Int, maxZoom: Int)

    /** The set of tiles already present in the database (after [open]). */
    fun existingTiles(): Set<TileRef>

    /** Inserts one tile. [tmsRow] is already flipped to TMS order by the packer. */
    fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray)

    fun close()
}

class MbtilesTilePackager(
    private val fetcher: TileFetcher,
    private val store: MbtilesStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun download(
        region: Region,
        minZoom: Int = OfflineLimits.MIN_ZOOM,
        maxZoom: Int = OfflineLimits.MAX_ZOOM,
        maxTiles: Int = OfflineLimits.MAX_TILES,
        concurrency: Int = 4,
        // Wi-Fi/LTE hand-offs and idle sockets are transient: every tile gets
        // this many fetch attempts with exponential backoff before the batch
        // is treated as a network failure. Callers that need fail-fast
        // behaviour (tests, tiny probes) pass 1.
        maxFetchAttempts: Int = DEFAULT_FETCH_ATTEMPTS,
        fetchRetryBaseDelayMs: Long = FETCH_RETRY_BASE_DELAY_MS,
        onProgress: (Float, String) -> Unit,
        // Suspend so callers can run post-download work (e.g. registering the
        // area in a repository) before being told "done".
        onSuccess: suspend (Int) -> Unit,
        onError: suspend (String) -> Unit
    ) = withContext(dispatcher) {
        val tiles = enumerateTiles(region, minZoom, maxZoom)
        val total = tiles.size

        if (total == 0) {
            onError("Obszar nie zawiera żadnych kafelków.")
            return@withContext
        }

        if (total > maxTiles) {
            onError("Obszar jest zbyt duży! Przybliż mapę, aby pobrać mniejszy wycinek (maksymalnie $maxTiles kafelków, aktualnie: $total).")
            return@withContext
        }

        try {
            store.open(
                bounds = "${region.lonWest},${region.latSouth},${region.lonEast},${region.latNorth}",
                minZoom = minZoom,
                maxZoom = maxZoom
            )

            val existingTiles = store.existingTiles()
            onProgress(0f, "Rozpoczynanie pobierania...")

            val writeMutex = Mutex()
            var successCount = 0
            for (batchStart in 0 until total step concurrency) {
                val batchEnd = minOf(batchStart + concurrency, total)
                val results = coroutineScope {
                    (batchStart until batchEnd).map { index ->
                        async(dispatcher) {
                            downloadTile(
                                tile = tiles[index],
                                existingTiles = existingTiles,
                                writeMutex = writeMutex,
                                maxFetchAttempts = maxFetchAttempts,
                                retryBaseDelayMs = fetchRetryBaseDelayMs
                            )
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never let an unexpected storage/open failure escape raw into the
            // caller's coroutine — on iOS an unhandled exception in this
            // standalone coroutine aborts the whole app (SIGABRT). Report it
            // through onError like any other download failure.
            println("MbtilesTilePackager: download failed: ${e.message}")
            onError("Błąd podczas pobierania: ${e.message}")
        } finally {
            try {
                store.close()
            } catch (e: Exception) {
                println("MbtilesTilePackager: store.close failed: ${e.message}")
            }
        }
    }

    private fun enumerateTiles(region: Region, minZoom: Int, maxZoom: Int): List<TileRef> {
        val tiles = mutableListOf<TileRef>()
        for (z in minZoom..maxZoom) {
            val x1 = TileMath.getTileX(region.lonWest, z)
            val x2 = TileMath.getTileX(region.lonEast, z)
            val y1 = TileMath.getTileY(region.latNorth, z)
            val y2 = TileMath.getTileY(region.latSouth, z)
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val startY = minOf(y1, y2)
            val endY = maxOf(y1, y2)
            for (x in minX..maxX) {
                for (y in startY..endY) {
                    tiles.add(TileRef(z, x, y))
                }
            }
        }
        return tiles
    }

    private sealed class TileResult {
        object Skipped : TileResult()
        object Downloaded : TileResult()
        object NotDownloaded : TileResult()
        object NetworkError : TileResult()
    }

    private suspend fun downloadTile(
        tile: TileRef,
        existingTiles: Set<TileRef>,
        writeMutex: Mutex,
        maxFetchAttempts: Int,
        retryBaseDelayMs: Long
    ): TileResult {
        val (z, x, y) = tile
        if (TileRef(z, x, y) in existingTiles) {
            return TileResult.Skipped
        }

        var attempt = 0
        while (true) {
            attempt++
            try {
                when (val result = fetcher.fetch(x, y, z)) {
                    is TileFetchResult.Ok -> {
                        if (result.bytes.isNotEmpty()) {
                            writeMutex.withLock {
                                store.putTile(z, x, (1 shl z) - 1 - y, result.bytes)
                            }
                            return TileResult.Downloaded
                        } else {
                            return TileResult.NotDownloaded
                        }
                    }
                    TileFetchResult.Empty -> return TileResult.NotDownloaded
                    TileFetchResult.NetworkError -> {
                        if (attempt >= maxFetchAttempts) return TileResult.NetworkError
                        delay(retryDelay(retryBaseDelayMs, attempt))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("MbtilesTilePackager: tile ${tile.z}/${tile.x}/${tile.y} failed: ${e.message}")
                return TileResult.NotDownloaded
            }
        }
    }

    private fun retryDelay(baseDelayMs: Long, attempt: Int): Long =
        minOf(baseDelayMs shl (attempt - 1), MAX_FETCH_RETRY_DELAY_MS)

    companion object {
        /** One initial try plus three retries: 0.5s, 1s, 2s of backoff. */
        const val DEFAULT_FETCH_ATTEMPTS = 4
        const val FETCH_RETRY_BASE_DELAY_MS = 500L
        const val MAX_FETCH_RETRY_DELAY_MS = 8_000L
    }
}