package com.indiana.zwl.presentation.map

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.OfflineAreaFiles
import com.indiana.zwl.shared.offline.TileFetchResult
import com.indiana.zwl.shared.offline.TileFetcher
import com.indiana.zwl.shared.offline.TileRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory [OfflineAreaFiles] — files are byte arrays keyed by name. */
class FakeOfflineAreaFiles : OfflineAreaFiles {
    val files = mutableMapOf<String, ByteArray>()
    var now = 1_700_000_000_000L

    override fun areasDirPath(): String = "/fake/areas"

    override fun filePath(fileName: String): String = "/fake/areas/$fileName"

    override fun fileSize(fileName: String): Long = files[fileName]?.size?.toLong() ?: 0L

    override fun fileExists(fileName: String): Boolean = files.containsKey(fileName)

    override fun deleteFile(fileName: String): Boolean {
        var removedAnything = false
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            if (files.remove(fileName + suffix) != null) removedAnything = true
        }
        return removedAnything
    }

    override fun listFileNames(): List<String> = files.keys.filter { it.endsWith(".mbtiles") }

    override fun nowMillis(): Long = now++
}

/** In-memory [OfflineAreaRepository] mirroring the SQLDelight-backed impl. */
class InMemoryOfflineAreaRepository : OfflineAreaRepository {
    val items = mutableListOf<DownloadedArea>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<DownloadedArea>> =
        flowOf(items.sortedByDescending { it.downloadedAt })

    override suspend fun getAll(): List<DownloadedArea> = items.sortedByDescending { it.downloadedAt }

    override suspend fun insert(area: NewDownloadedArea): Long {
        val id = nextId++
        items.add(
            DownloadedArea(
                id = id,
                name = area.name,
                fileName = area.fileName,
                latSouth = area.latSouth,
                latNorth = area.latNorth,
                lonWest = area.lonWest,
                lonEast = area.lonEast,
                minZoom = area.minZoom,
                maxZoom = area.maxZoom,
                tileCount = area.tileCount,
                fileSizeBytes = area.fileSizeBytes,
                downloadedAt = area.downloadedAt
            )
        )
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(name = name)
    }

    override suspend fun markRefreshed(
        id: Long,
        fileName: String,
        tileCount: Int,
        fileSizeBytes: Long,
        downloadedAt: Long
    ) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(
                fileName = fileName,
                tileCount = tileCount,
                fileSizeBytes = fileSizeBytes,
                downloadedAt = downloadedAt
            )
        }
    }

    override suspend fun delete(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}

class FakeStore : MbtilesStore {
    var openCount = 0
    var closeCount = 0

    override fun open(bounds: String, minZoom: Int, maxZoom: Int) {
        openCount++
    }

    override fun existingTiles(): Set<TileRef> = emptySet()

    override fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray) {}

    override fun close() {
        closeCount++
    }
}

class FakeFetcher(
    private val result: TileFetchResult = TileFetchResult.Ok(byteArrayOf(1, 2, 3))
) : TileFetcher {
    override suspend fun fetch(x: Int, y: Int, z: Int): TileFetchResult = result
}
