package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.MbtilesTilePackager
import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.offline.TileFetchResult
import com.indiana.zwl.shared.offline.TileFetcher
import com.indiana.zwl.shared.offline.TileRef
import com.indiana.zwl.shared.offline.TileMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MbtilesTilePackagerTest {

    private class FakeStore : MbtilesStore {
        var openCount = 0
        var closeCount = 0
        var emptyCount = 0
        val written = mutableListOf<CapturedTile>()

        class CapturedTile(val zoom: Int, val column: Int, val tmsRow: Int, val blob: ByteArray)

        override fun open(bounds: String, minZoom: Int, maxZoom: Int) {
            openCount++
        }

        override fun existingTiles(): Set<TileRef> = emptySet()

        override fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray) {
            written.add(CapturedTile(zoom, column, tmsRow, blob))
        }

        override fun close() {
            closeCount++
        }
    }

    private class FakeFetcher(
        private val result: TileFetchResult = TileFetchResult.Ok(byteArrayOf(1, 2, 3)),
        val fetchCounts: MutableList<Triple<Int, Int, Int>> = mutableListOf()
    ) : TileFetcher {
        override suspend fun fetch(x: Int, y: Int, z: Int): TileFetchResult {
            fetchCounts.add(Triple(x, y, z))
            return result
        }
    }

    private fun packer(fetcher: TileFetcher, store: MbtilesStore) =
        MbtilesTilePackager(fetcher = fetcher, store = store, dispatcher = Dispatchers.Unconfined)

    private class Driver(
        val store: FakeStore = FakeStore(),
        val fetches: MutableList<Triple<Int, Int, Int>> = mutableListOf(),
        val errors: MutableList<String> = mutableListOf(),
        var successCount: Int? = null
    )

    private fun run(
        region: Region,
        packer: MbtilesTilePackager,
        fetcher: FakeFetcher,
        store: FakeStore
    ): Driver = runBlocking {
        val driver = Driver(store, fetcher.fetchCounts)
        packer.download(
            region = region,
            onProgress = { _, _ -> },
            onSuccess = { driver.successCount = it },
            onError = { driver.errors.add(it) }
        )
        driver
    }

    @Test
    fun `store is not opened when area exceeds tile cap`() = runBlocking {
        val store = FakeStore()
        val fetcher = FakeFetcher()
        val driver = run(
            region = Region(49.0, 55.0, 14.0, 24.0),
            packer = packer(fetcher, store),
            fetcher = fetcher,
            store = store
        )

        assertEquals(0, store.openCount)
        assertEquals(0, store.closeCount)
        assertEquals(null, driver.successCount)
        assertEquals(1, driver.errors.size)
        assertTrue(driver.errors[0].contains("zbyt duży"))
    }

    @Test
    fun `tiles are stored with TMS flipped row`() = runBlocking {
        val store = FakeStore()
        val fetcher = FakeFetcher()
        // Single-tile region at zoom 10.
        val region = Region(
            latSouth = 52.20,
            latNorth = 52.25,
            lonWest = 21.00,
            lonEast = 21.05
        )
        val packer = MbtilesTilePackager(fetcher, store, Dispatchers.Unconfined)

        var success: Int? = null
        var error: String? = null
        packer.download(
            region = region,
            onProgress = { _, _ -> },
            onSuccess = { success = it },
            onError = { error = it }
        )

        assertEquals(null, error)
        assertTrue((success ?: 0) > 0)
        assertEquals(1, store.openCount)
        assertEquals(1, store.closeCount)
        assertFalse(store.written.isEmpty())

        val tile = store.written.first()
        val expectedFaceY = TileMath.getTileY(region.latNorth, 10)
        assertEquals((1 shl 10) - 1 - expectedFaceY, tile.tmsRow)
        assertEquals(store.written.size, success)
    }

    @Test
    fun `network error aborts download with error message`() = runBlocking {
        val store = FakeStore()
        val fetcher = FakeFetcher(TileFetchResult.NetworkError)
        val driver = run(
            region = Region(52.20, 52.25, 21.00, 21.05),
            packer = packer(fetcher, store),
            fetcher = fetcher,
            store = store
        )

        assertEquals(null, driver.successCount)
        assertEquals(1, driver.errors.size)
        assertTrue(driver.errors[0].contains("Błąd połączenia"))
        assertEquals(1, store.closeCount)
    }
}