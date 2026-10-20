package com.indiana.zwl.shared.offline

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Platform-neutral orchestration of per-area offline downloads: packs tiles
 * into a dedicated `area_<epochMillis>.mbtiles` file, registers the area in
 * the repository on success and removes the file whenever the packer does not
 * end in a committed state (failure, unexpected exception **and** cancellation)
 * — so an aborted download never leaves a half-written area.
 *
 * [nameFormatter] builds the auto display name ("Obszar 03.09 14:32") from
 * the download start timestamp.
 */
class OfflineAreaDownloadCoordinator(
    private val repository: OfflineAreaRepository,
    private val storeFactory: MbtilesStoreFactory,
    private val fetcherProvider: () -> TileFetcher,
    private val files: OfflineAreaFiles,
    private val nameFormatter: (Long) -> String,
    // Downloads are blocking I/O (HTTP + SQLite). Android's worker passes
    // Dispatchers.IO so the tile pool never steals threads from the CPU pool.
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun download(
        region: Region,
        minZoom: Int = OfflineLimits.MIN_ZOOM,
        maxZoom: Int = OfflineLimits.MAX_ZOOM,
        // Callers that can restart the download (Android's tile worker, whose
        // input data survives a process kill) inject the start time so the
        // file name — and every tile already written into it — is stable
        // across restarts: the packer skips existing tiles and the run
        // resumes instead of starting over.
        startedAt: Long? = null,
        // Keep the partial file when the download is cancelled, so the next
        // run resumes from the tiles already packed. Real failures always
        // clean up after themselves.
        keepPartialOnCancellation: Boolean = false,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val effectiveStartedAt = startedAt ?: files.nowMillis()
        val fileName = "area_$effectiveStartedAt.mbtiles"
        val packager = MbtilesTilePackager(fetcherProvider(), storeFactory.create(fileName), dispatcher)
        var committed = false
        var cancelled = false
        try {
            packager.download(
                region = region,
                minZoom = minZoom,
                maxZoom = maxZoom,
                onProgress = onProgress,
                onSuccess = { count ->
                    try {
                        repository.insert(
                            NewDownloadedArea(
                                name = nameFormatter(effectiveStartedAt),
                                fileName = fileName,
                                latSouth = region.latSouth,
                                latNorth = region.latNorth,
                                lonWest = region.lonWest,
                                lonEast = region.lonEast,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                tileCount = count,
                                fileSizeBytes = files.fileSize(fileName),
                                downloadedAt = effectiveStartedAt
                            )
                        )
                        committed = true
                        onSuccess(count)
                    } catch (e: Exception) {
                        println("OfflineAreaDownloadCoordinator: registration failed: ${e.message}")
                        onError("Błąd podczas rejestracji obszaru: ${e.message}")
                    }
                },
                onError = { msg -> onError(msg) }
            )
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } finally {
            // Covers failure, unexpected exceptions and CancellationException
            // (the packager rethrows it): never keep a partial area — unless the
            // caller asked to keep it so a restart can resume from it.
            if (!committed && !(cancelled && keepPartialOnCancellation)) {
                files.deleteFile(fileName)
            } else if (!committed) {
                println("OfflineAreaDownloadCoordinator: kept partial $fileName for resume")
            }
        }
    }

    /**
     * Re-downloads [area]'s bbox into a fresh file and swaps it in only on
     * success — a failed or cancelled refresh leaves the existing data
     * untouched.
     */
    suspend fun refresh(
        area: DownloadedArea,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val startedAt = files.nowMillis()
        val newFileName = "area_$startedAt.mbtiles"
        val packager = MbtilesTilePackager(fetcherProvider(), storeFactory.create(newFileName), dispatcher)
        val region = Region(area.latSouth, area.latNorth, area.lonWest, area.lonEast)
        var committed = false
        try {
            packager.download(
                region = region,
                minZoom = area.minZoom,
                maxZoom = area.maxZoom,
                onProgress = onProgress,
                onSuccess = { count ->
                    try {
                        repository.markRefreshed(
                            id = area.id,
                            fileName = newFileName,
                            tileCount = count,
                            fileSizeBytes = files.fileSize(newFileName),
                            downloadedAt = startedAt
                        )
                        // The record now points at the new file — keep it even
                        // if removing the stale old file were to misbehave.
                        committed = true
                        files.deleteFile(area.fileName)
                        onSuccess(count)
                    } catch (e: Exception) {
                        println("OfflineAreaDownloadCoordinator: refresh registration failed: ${e.message}")
                        onError("Błąd podczas odświeżania obszaru: ${e.message}")
                    }
                },
                onError = { msg -> onError(msg) }
            )
        } finally {
            // On failure/cancellation drop the new file and keep the old one.
            if (!committed) files.deleteFile(newFileName)
        }
    }
}
