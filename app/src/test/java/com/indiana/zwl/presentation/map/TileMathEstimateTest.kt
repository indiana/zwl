package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.offline.TileMath
import org.junit.Assert.assertEquals
import org.junit.Test

class TileMathEstimateTest {

    @Test
    fun `point region yields one tile per zoom level`() {
        // A degenerate (point) region always lands in a single tile, so the
        // estimate for zooms 10..16 must be exactly 7.
        val total = TileMath.estimateTileCount(Region(52.23, 52.23, 21.01, 21.01))
        assertEquals(7, total)
    }

    @Test
    fun `estimate matches explicit zoom range`() {
        val region = Region(52.20, 52.25, 21.00, 21.05)
        val z10 = TileMath.estimateTileCount(region, minZoom = 10, maxZoom = 10)
        val z10_11 = TileMath.estimateTileCount(region, minZoom = 10, maxZoom = 11)
        // Adding a zoom level multiplies that level's tiles by 4 relative to
        // the previous level, so the total strictly grows.
        assert(z10_11 > z10)
        // Single level: the region spans 1 tile wide... at z10 this bbox is
        // within one tile column/row, so the single-level count is 1.
        assertEquals(1, z10)
    }
}
