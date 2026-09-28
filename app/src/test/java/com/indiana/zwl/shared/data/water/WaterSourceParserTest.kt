package com.indiana.zwl.shared.data.water

import com.indiana.zwl.domain.model.DrinkingWaterStatus
import com.indiana.zwl.domain.model.WaterSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterSourceParserTest {

    private fun feature(
        osmId: String = "n1",
        type: String = "SPRING",
        drinkingWater: String? = null,
        depthMeters: String? = null,
        geometryType: String = "Point",
        coordinates: String = "[19.0,52.0]"
    ): String {
        val props = buildString {
            append("\"osmId\":\"$osmId\",\"type\":\"$type\"")
            drinkingWater?.let { append(",\"drinkingWater\":\"$it\"") }
            depthMeters?.let { append(",\"depthMeters\":$it") }
        }
        return "{\"type\":\"Feature\",\"properties\":{$props},\"geometry\":{\"type\":\"$geometryType\",\"coordinates\":$coordinates}}"
    }

    private fun collection(vararg features: String): String =
        "{\"type\":\"FeatureCollection\",\"features\":[${features.joinToString(",")}]}"

    @Test
    fun `maps every water source type`() {
        val json = collection(
            feature(type = "DRINKING_WATER"),
            feature(osmId = "n2", type = "WATER_TAP"),
            feature(osmId = "n3", type = "WATER_POINT"),
            feature(osmId = "n4", type = "SPRING"),
            feature(osmId = "n5", type = "WELL"),
            feature(osmId = "n6", type = "FOUNTAIN")
        )

        val types = WaterSourceParser.parse(json).map { it.type }

        assertEquals(
            listOf(
                WaterSourceType.DRINKING_WATER,
                WaterSourceType.WATER_TAP,
                WaterSourceType.WATER_POINT,
                WaterSourceType.SPRING,
                WaterSourceType.WELL,
                WaterSourceType.FOUNTAIN
            ),
            types
        )
    }

    @Test
    fun `maps drinking water tri-state`() {
        val json = collection(
            feature(type = "SPRING", drinkingWater = "YES"),
            feature(osmId = "n2", type = "SPRING", drinkingWater = "NO"),
            feature(osmId = "n3", type = "SPRING"),
            feature(osmId = "n4", type = "SPRING", drinkingWater = "unknown")
        )

        val statuses = WaterSourceParser.parse(json).map { it.drinkingWater }

        assertEquals(
            listOf(
                DrinkingWaterStatus.YES,
                DrinkingWaterStatus.NO,
                DrinkingWaterStatus.UNKNOWN,
                DrinkingWaterStatus.UNKNOWN
            ),
            statuses
        )
    }

    @Test
    fun `ignores non point geometry`() {
        val json = collection(
            feature(osmId = "n1", type = "SPRING"),
            feature(osmId = "n2", type = "SPRING", geometryType = "Polygon", coordinates = "[[[19.0,52.0],[19.1,52.0],[19.1,52.1],[19.0,52.0]]]")
        )

        val parsed = WaterSourceParser.parse(json)

        assertEquals(1, parsed.size)
        assertEquals("n1", parsed.first().osmId)
    }

    @Test
    fun `parses depth and coordinates`() {
        val json = collection(feature(type = "WELL", depthMeters = "30.5", coordinates = "[19.123456,52.654321]"))

        val parsed = WaterSourceParser.parse(json).single()

        assertEquals(30.5, parsed.depthMeters!!, 0.0001)
        assertEquals(19.123456, parsed.longitude, 0.000001)
        assertEquals(52.654321, parsed.latitude, 0.000001)
        assertEquals("OSM", parsed.source)
    }

    @Test
    fun `drops features without osmId or with unknown type`() {
        val json = collection(
            feature(osmId = "", type = "SPRING"),
            feature(osmId = "n2", type = "NOT_A_TYPE"),
            feature(osmId = "n3", type = "SPRING")
        )

        val parsed = WaterSourceParser.parse(json)

        assertEquals(1, parsed.size)
        assertEquals("n3", parsed.first().osmId)
    }

    @Test
    fun `returns empty list for empty collection`() {
        assertTrue(WaterSourceParser.parse(collection()).isEmpty())
    }
}
