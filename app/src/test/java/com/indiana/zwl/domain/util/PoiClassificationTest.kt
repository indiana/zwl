package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.Poi
import org.junit.Assert.assertEquals
import org.junit.Test

class PoiClassificationTest {

    private fun poi(code: String, name: String = "") = Poi(
        id = 0,
        code = code,
        description = "",
        name = name,
        latitude = 0.0,
        longitude = 0.0
    )

    @Test
    fun `PT WODOW classifies as WATER`() {
        assertEquals(PoiCategory.WATER, poi("PT WODOW").classify())
    }

    @Test
    fun `PT WODOW maps to the water launch ui group`() {
        assertEquals(PoiUiGroup.WATER_LAUNCH, poi("PT WODOW").classify().uiGroup())
    }

    @Test
    fun `water launch group key and label are stable`() {
        assertEquals("wodowanie", PoiUiGroup.WATER_LAUNCH.key)
        assertEquals("Wodowanie sprzętu wodnego", PoiUiGroup.WATER_LAUNCH.label)
    }

    @Test
    fun `viewpoints keep their own group`() {
        assertEquals(PoiUiGroup.VIEWPOINT, poi("PKT WIDOK").classify().uiGroup())
    }
}
