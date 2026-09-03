package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.MbtilesStoreFactory
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.OfflineAreaDownloadCoordinator
import com.indiana.zwl.shared.offline.Region
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAreaDownloadCoordinatorTest {

    private class Callbacks {
        var success: Int? = null
        var error: String? = null
    }

    private fun coordinator(
        repo: InMemoryOfflineAreaRepository,
        files: FakeOfflineAreaFiles,
        fetcher: FakeFetcher,
        stores: MutableList<FakeStore>
    ): OfflineAreaDownloadCoordinator = OfflineAreaDownloadCoordinator(
        repository = repo,
        storeFactory = MbtilesStoreFactory {
            FakeStore().also { stores.add(it) }
        },
        fetcherProvider = { fetcher },
        files = files,
        nameFormatter = { "TEST_NAME" }
    )

    @Test
    fun `successful download registers area and keeps file`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        val files = FakeOfflineAreaFiles()
        val stores = mutableListOf<FakeStore>()
        val expectedFile = "area_${files.now}.mbtiles"
        // The real store writes into the file; the fake store does not, so
        // seed the file to stand in for packed tile bytes.
        files.files[expectedFile] = byteArrayOf(1, 2, 3, 4)
        val callbacks = Callbacks()

        coordinator(repo, files, FakeFetcher(), stores).download(
            region = Region(52.20, 52.25, 21.00, 21.05),
            onProgress = { _, _ -> },
            onSuccess = { callbacks.success = it },
            onError = { callbacks.error = it }
        )

        assertEquals(null, callbacks.error)
        assertEquals(1, repo.items.size)
        val area = repo.items.first()
        assertEquals(expectedFile, area.fileName)
        assertEquals("TEST_NAME", area.name)
        assertEquals(4L, area.fileSizeBytes)
        assertEquals(OfflineLimits.MIN_ZOOM, area.minZoom)
        assertEquals(OfflineLimits.MAX_ZOOM, area.maxZoom)
        assertTrue(area.downloadedAt > 0)
        assertTrue(files.files.containsKey(expectedFile))
        assertEquals(1, stores.size)
        assertEquals(1, stores.first().closeCount)
    }

    @Test
    fun `failed download removes file and does not register area`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        val files = FakeOfflineAreaFiles()
        val stores = mutableListOf<FakeStore>()
        val expectedFile = "area_${files.now}.mbtiles"
        files.files[expectedFile] = byteArrayOf(1)
        val callbacks = Callbacks()

        coordinator(repo, files, FakeFetcher(com.indiana.zwl.shared.offline.TileFetchResult.NetworkError), stores)
            .download(
                region = Region(52.20, 52.25, 21.00, 21.05),
                onProgress = { _, _ -> },
                onSuccess = { callbacks.success = it },
                onError = { callbacks.error = it }
            )

        assertEquals(null, callbacks.success)
        assertTrue(callbacks.error!!.contains("Błąd połączenia"))
        assertTrue(repo.items.isEmpty())
        assertFalse(files.files.containsKey(expectedFile))
    }

    @Test
    fun `refresh swaps file and updates record`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        val files = FakeOfflineAreaFiles()
        val stores = mutableListOf<FakeStore>()
        files.files["area_old.mbtiles"] = byteArrayOf(9, 9)
        repo.insert(
            com.indiana.zwl.domain.model.NewDownloadedArea(
                name = "Old", fileName = "area_old.mbtiles",
                latSouth = 52.0, latNorth = 52.1, lonWest = 21.0, lonEast = 21.1,
                minZoom = 10, maxZoom = 16, tileCount = 10,
                fileSizeBytes = 2, downloadedAt = 1_000
            )
        )
        val area = repo.items.first()
        val newFile = "area_${files.now}.mbtiles"
        files.files[newFile] = byteArrayOf(1, 2, 3)
        val callbacks = Callbacks()

        coordinator(repo, files, FakeFetcher(), stores).refresh(
            area = area,
            onProgress = { _, _ -> },
            onSuccess = { callbacks.success = it },
            onError = { callbacks.error = it }
        )

        assertEquals(null, callbacks.error)
        assertEquals(1, repo.items.size)
        val updated = repo.items.first()
        assertEquals(area.id, updated.id)
        assertEquals(newFile, updated.fileName)
        assertEquals(3L, updated.fileSizeBytes)
        assertTrue(updated.downloadedAt > area.downloadedAt)
        assertFalse(files.files.containsKey("area_old.mbtiles"))
        assertTrue(files.files.containsKey(newFile))
    }

    @Test
    fun `failed refresh keeps old data untouched`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        val files = FakeOfflineAreaFiles()
        val stores = mutableListOf<FakeStore>()
        files.files["area_old.mbtiles"] = byteArrayOf(9, 9)
        repo.insert(
            com.indiana.zwl.domain.model.NewDownloadedArea(
                name = "Old", fileName = "area_old.mbtiles",
                latSouth = 52.0, latNorth = 52.1, lonWest = 21.0, lonEast = 21.1,
                minZoom = 10, maxZoom = 16, tileCount = 10,
                fileSizeBytes = 2, downloadedAt = 1_000
            )
        )
        val area = repo.items.first()
        val callbacks = Callbacks()

        coordinator(repo, files, FakeFetcher(com.indiana.zwl.shared.offline.TileFetchResult.NetworkError), stores)
            .refresh(
                area = area,
                onProgress = { _, _ -> },
                onSuccess = { callbacks.success = it },
                onError = { callbacks.error = it }
            )

        assertEquals(null, callbacks.success)
        assertTrue(callbacks.error != null)
        assertEquals(1, repo.items.size)
        assertEquals("area_old.mbtiles", repo.items.first().fileName)
        assertTrue(files.files.containsKey("area_old.mbtiles"))
        assertEquals(1, files.files.size)
    }
}
