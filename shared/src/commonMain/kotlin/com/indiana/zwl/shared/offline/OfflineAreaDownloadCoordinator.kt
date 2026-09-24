package com.indiana.zwl.shared.offline

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository

/**
 * Platform-neutral orchestration of per-area offline downloads: packs tiles
 * into a dedicated `area_<epochMillis>.mbtiles` file, registers the area in
 * the repository on success and removes the file whenever the packer does not
 * end in a committed state (failure, unexpected exception **and
 * cancellation**) — so an aborted download never leaves a half-written area.
 *
 * [nameFormatter] builds the auto display name ("Obszar 03.09 14:32") from
 * the download start timestamp.
 */
class OfflineAreaDownloadCoordinator(
    private val repository: OfflineAreaRepository,
    private val storeFactory: MbtilesStoreFactory,
    private val fetcherProvider: () -> TileFetcher,
    private val files: OfflineAreaFiles,
    private val nameFormatter: (Long) -> String
) {

    suspend fun download(
        region: Region,
        minZoom: Int = OfflineLimits.MIN_ZOOM,
        maxZoom: Int = OfflineLimits.MAX_ZOOM,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val startedAt = files.nowMillis()
        val fileName = "area_$startedAt.mbtiles"
        val packager = MbtilesTilePackager(fetcherProvider(), storeFactory.create(fileName))
        var committed = false
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
                                name = nameFormatter(startedAt),
                                fileName = fileName,
                                latSouth = region.latSouth,
                                latNorth = region.latNorth,
                                lonWest = region.lonWest,
                                lonEast = region.lonEast,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                tileCount = count,
                                fileSizeBytes = files.fileSize(fileName),
                                downloadedAt = startedAt
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
        } finally {
            // Covers failure, unexpected exceptions and CancellationException
            // (the packager rethrows it): never keep a partial area.
            if (!committed) files.deleteFile(fileName)
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
        val packager = MbtilesTilePackager(fetcherProvider(), storeFactory.create(newFileName))
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
