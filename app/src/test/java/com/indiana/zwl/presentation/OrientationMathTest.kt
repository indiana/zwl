package com.indiana.zwl.presentation

import com.indiana.zwl.presentation.map.util.OrientationMath
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationMathTest {

    @Test
    fun `shortestAngleDelta crosses the wrap in the correct direction`() {
        assertEquals(1.0, OrientationMath.shortestAngleDelta(359.0, 0.0), 1e-9)
        assertEquals(-1.0, OrientationMath.shortestAngleDelta(0.0, 359.0), 1e-9)
        assertEquals(10.0, OrientationMath.shortestAngleDelta(350.0, 0.0), 1e-9)
        assertEquals(-170.0, OrientationMath.shortestAngleDelta(10.0, 200.0), 1e-9)
        assertEquals(180.0, OrientationMath.shortestAngleDelta(0.0, 180.0), 1e-9)
    }

    @Test
    fun `bearingDifference is symmetric and capped at 180`() {
        assertEquals(5.0, OrientationMath.bearingDifference(1.0, 356.0), 1e-9)
        assertEquals(180.0, OrientationMath.bearingDifference(0.0, 180.0), 1e-9)
    }

    @Test
    fun `stepTowards never overshoots the target across the wrap`() {
        assertEquals(180.0, OrientationMath.stepTowards(10.0, 180.0, 200.0), 1e-9)
        assertEquals(0.0, OrientationMath.stepTowards(20.0, 0.0, 20.0), 1e-9)
        assertEquals(0.0, OrientationMath.stepTowards(2.0, 0.0, 5.0), 1e-9)
        assertEquals(0.0, OrientationMath.stepTowards(359.0, 0.0, 1.0), 1e-9)
    }
}
