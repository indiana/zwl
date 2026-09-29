package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.WaterSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class WaterSourceClassificationTest {

    @Test
    fun `maps every water source type to its group`() {
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.DRINKING_WATER.waterGroup())
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.WATER_TAP.waterGroup())
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.WATER_POINT.waterGroup())
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.FOUNTAIN.waterGroup())
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.WATER_ON_SITE.waterGroup())
        assertEquals(WaterSourceGroup.SPRING, WaterSourceType.SPRING.waterGroup())
        assertEquals(WaterSourceGroup.WELL, WaterSourceType.WELL.waterGroup())
        assertEquals(WaterSourceGroup.DRINKING, WaterSourceType.REFILL.waterGroup())
    }
}
