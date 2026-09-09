package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.OfflineAreaJanitor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAreaJanitorTest {

    @Test
    fun `removes legacy cache and orphans but keeps registered areas`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        repo.insert(
            com.indiana.zwl.domain.model.NewDownloadedArea(
                name = "A", fileName = "area_1.mbtiles",
                latSouth = 52.0, latNorth = 52.1, lonWest = 21.0, lonEast = 21.1,
                minZoom = 10, maxZoom = 16, tileCount = 5,
                fileSizeBytes = 3, downloadedAt = 1_000
            )
        )
        val files = FakeOfflineAreaFiles()
        files.files["map.mbtiles"] = byteArrayOf(1)
        files.files["map.mbtiles-wal"] = byteArrayOf(1)
        files.files["area_1.mbtiles"] = byteArrayOf(1)
        files.files["area_999.mbtiles"] = byteArrayOf(1)

        OfflineAreaJanitor(repo, files).run()

        assertFalse(files.files.containsKey("map.mbtiles"))
        assertFalse(files.files.containsKey("map.mbtiles-wal"))
        assertTrue(files.files.containsKey("area_1.mbtiles"))
        assertFalse(files.files.containsKey("area_999.mbtiles"))
        assertEquals(1, files.files.size)
    }

    @Test
    fun `removes ghost rows whose file is missing`() = runBlocking {
        val repo = InMemoryOfflineAreaRepository()
        // Registered area whose file the user deleted outside the app...
        repo.insert(
            com.indiana.zwl.domain.model.NewDownloadedArea(
                name = "Ghost", fileName = "area_ghost.mbtiles",
                latSouth = 52.0, latNorth = 52.1, lonWest = 21.0, lonEast = 21.1,
                minZoom = 10, maxZoom = 16, tileCount = 5,
                fileSizeBytes = 3, downloadedAt = 1_000
            )
        )
        // ...and a healthy one.
        repo.insert(
            com.indiana.zwl.domain.model.NewDownloadedArea(
                name = "Healthy", fileName = "area_ok.mbtiles",
                latSouth = 52.0, latNorth = 52.1, lonWest = 21.0, lonEast = 21.1,
                minZoom = 10, maxZoom = 16, tileCount = 5,
                fileSizeBytes = 3, downloadedAt = 2_000
            )
        )
        val files = FakeOfflineAreaFiles()
        files.files["area_ok.mbtiles"] = byteArrayOf(1)

        OfflineAreaJanitor(repo, files).run()

        assertEquals(1, repo.items.size)
        assertEquals("area_ok.mbtiles", repo.items.first().fileName)
    }

    @Test
    fun `survives empty store`() = runBlocking {
        val files = FakeOfflineAreaFiles()
        OfflineAreaJanitor(InMemoryOfflineAreaRepository(), files).run()
        assertTrue(files.files.isEmpty())
    }
}
