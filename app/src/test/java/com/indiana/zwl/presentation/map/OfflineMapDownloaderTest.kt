package com.indiana.zwl.presentation.map

import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.BoundingBox

class OfflineMapDownloaderTest {

    private val client = OkHttpClient()
    private val tileCache = mockk<org.mapsforge.map.layer.cache.TileCache>(relaxed = true)

    @Test
    fun `tileRanges matches getTileX and getTileY bounds`() {
        val bbox = BoundingBox(52.40, 16.90, 52.42, 16.95)
        val ranges = OfflineMapDownloader.tileRanges(bbox, 10..16)

        assertEquals((10..16).count(), ranges.size)

        val z10 = ranges.first { it.zoom == 10 }
        assertEquals(
            OfflineMapDownloader.getTileX(16.95, 10),
            z10.maxX
        )
        assertEquals(
            OfflineMapDownloader.getTileX(16.90, 10),
            z10.minX
        )
        assertEquals(
            maxOf(
                OfflineMapDownloader.getTileY(52.42, 10),
                OfflineMapDownloader.getTileY(52.40, 10)
            ),
            z10.endY
        )

        val count = OfflineMapDownloader.countTiles(ranges)
        assertTrue(count > 0)
    }

    @Test
    fun `country scale bbox is rejected before any downloads start`() = runTest {
        val bbox = BoundingBox(49.0, 14.0, 55.0, 24.0)

        val ranges = OfflineMapDownloader.tileRanges(bbox, 10..16)
        assertTrue(OfflineMapDownloader.countTiles(ranges) > OfflineMapDownloader.MAX_TILES)

        val statuses = OfflineMapDownloader
            .downloadArea(bbox, 256, tileCache, client)
            .toList()

        assertEquals(1, statuses.size)
        val message = statuses.single() as? DownloadStatus.Message
        assertTrue(
            "Expected too-big message, got ${statuses.single()}",
            message?.msg?.contains("zbyt duży") == true
        )
    }

    @Test
    fun `tiny bbox passes validation and starts with bounded total`() = runTest {
        val bbox = BoundingBox(52.4080, 16.9250, 52.4095, 16.9270)

        val first = OfflineMapDownloader
            .downloadArea(bbox, 256, tileCache, client)
            .take(1)
            .toList()

        val start = first.single() as? DownloadStatus.Start
        assertTrue("Expected DownloadStatus.Start, got ${first.single()}", start != null)
        assertTrue(start!!.total in 1..OfflineMapDownloader.MAX_TILES)
    }
}
