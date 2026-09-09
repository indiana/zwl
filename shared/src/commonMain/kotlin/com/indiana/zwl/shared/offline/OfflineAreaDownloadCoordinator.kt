package com.indiana.zwl.shared.offline

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository

/**
 * Platform-neutral orchestration of per-area offline downloads: packs tiles
 * into a dedicated `area_<epochMillis>.mbtiles` file, registers the area in
 * the repository on success and removes the file on failure (no half-written
 * orphans while the app runs; the janitor sweeps leftovers after a kill).
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
        maxTiles: Int = OfflineLimits.MAX_TILES,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val startedAt = files.nowMillis()
        val fileName = "area_$startedAt.mbtiles"
        val packager = MbtilesTilePackager(fetcherProvider(), storeFactory.create(fileName))
        packager.download(
            region = region,
            minZoom = minZoom,
            maxZoom = maxZoom,
            maxTiles = maxTiles,
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
                    onSuccess(count)
                } catch (e: Exception) {
                    println("OfflineAreaDownloadCoordinator: registration failed: ${e.message}")
                    files.deleteFile(fileName)
                    onError("Błąd podczas rejestracji obszaru: ${e.message}")
                }
            },
            onError = { msg ->
                files.deleteFile(fileName)
                onError(msg)
            }
        )
    }

    /**
     * Re-downloads [area]'s bbox into a fresh file and swaps it in only on
     * success — a failed refresh leaves the existing data untouched.
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
        packager.download(
            region = region,
            minZoom = area.minZoom,
            maxZoom = area.maxZoom,
            maxTiles = OfflineLimits.MAX_TILES,
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
                    files.deleteFile(area.fileName)
                    onSuccess(count)
                } catch (e: Exception) {
                    println("OfflineAreaDownloadCoordinator: refresh registration failed: ${e.message}")
                    files.deleteFile(newFileName)
                    onError("Błąd podczas odświeżania obszaru: ${e.message}")
                }
            },
            onError = { msg ->
                files.deleteFile(newFileName)
                onError(msg)
            }
        )
    }
}
