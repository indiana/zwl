package com.indiana.zwl.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomAwareMarkerRadiusTest {

    private fun r(zoom: Int, scale: Float = 1f) =
        poiDotRadiusPx(zoom.toByte(), minDotZoom = 8, zoomThreshold = 13, scaleFactor = scale)

    @Test
    fun `radius ramps linearly between min and max zoom`() {
        assertEquals(3, r(8))
        assertEquals(4, r(9))
        assertEquals(5, r(10))
        assertEquals(6, r(11))
        assertEquals(7, r(12))
    }

    @Test
    fun `radius clamps outside the dot range`() {
        assertEquals(3, r(5))    // below map min zoom
        assertEquals(3, r(0))
        assertEquals(7, r(12))   // top of dot range (icon takes over at 13)
        assertEquals(7, r(Byte.MAX_VALUE.toInt()))
    }

    @Test
    fun `radius is monotonic non-decreasing in zoom`() {
        for (z in 0..12) {
            assertTrue("r($z) <= r(${z + 1})", r(z) <= r(z + 1))
        }
    }

    @Test
    fun `radius scales with display density`() {
        assertEquals((7f * 2f).toInt(), r(12, scale = 2f))
        assertEquals((3f * 3f).toInt(), r(8, scale = 3f))
        assertTrue(r(10, scale = 2f) > r(10, scale = 1f))
    }

    @Test
    fun `degenerate span never divides by zero`() {
        // zoomThreshold - 1 == minDotZoom → span coerced to 1;
        // zoomLevel == minDotZoom pins t to 0, so the MIN end is returned (correct, no div-by-zero).
        val radius = poiDotRadiusPx(12, minDotZoom = 12, zoomThreshold = 13, scaleFactor = 1f)
        assertEquals(3, radius)
    }
}
