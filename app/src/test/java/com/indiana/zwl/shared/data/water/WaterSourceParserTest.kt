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
        extraProperties: String? = null,
        geometryType: String = "Point",
        coordinates: String = "[19.0,52.0]"
    ): String {
        val props = buildString {
            append("\"osmId\":\"$osmId\",\"type\":\"$type\"")
            drinkingWater?.let { append(",\"drinkingWater\":\"$it\"") }
            depthMeters?.let { append(",\"depthMeters\":$it") }
            extraProperties?.let { append(",$it") }
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
            feature(osmId = "n6", type = "FOUNTAIN"),
            feature(osmId = "n7", type = "WATER_ON_SITE"),
            feature(osmId = "n8", type = "REFILL")
        )

        val types = WaterSourceParser.parse(json).map { it.type }

        assertEquals(
            listOf(
                WaterSourceType.DRINKING_WATER,
                WaterSourceType.WATER_TAP,
                WaterSourceType.WATER_POINT,
                WaterSourceType.SPRING,
                WaterSourceType.WELL,
                WaterSourceType.FOUNTAIN,
                WaterSourceType.WATER_ON_SITE,
                WaterSourceType.REFILL
            ),
            types
        )
    }

    @Test
    fun `parses optional enrichment properties`() {
        val json = collection(
            feature(
                type = "DRINKING_WATER",
                extraProperties = "\"pump\":\"manual\",\"drinkingWaterRaw\":\"treated\"," +
                    "\"seasonal\":\"yes\",\"intermittent\":\"no\",\"fee\":\"no\"," +
                    "\"openingHours\":\"24/7\",\"operator\":\"Gmina\",\"description\":\"Kran\"," +
                    "\"bottle\":\"yes\""
            ),
            feature(
                osmId = "n2",
                type = "FOUNTAIN",
                drinkingWater = "YES",
                extraProperties = "\"fountain\":\"bubbler\""
            )
        )

        val parsed = WaterSourceParser.parse(json)

        val first = parsed.first()
        assertEquals("manual", first.pump)
        assertEquals("treated", first.drinkingWaterRaw)
        assertEquals("yes", first.seasonal)
        assertEquals("no", first.intermittent)
        assertEquals("no", first.fee)
        assertEquals("24/7", first.openingHours)
        assertEquals("Gmina", first.operatorName)
        assertEquals("Kran", first.waterDescription)
        assertEquals("yes", first.bottle)
        assertEquals("bubbler", parsed[1].fountain)
        assertEquals(null, parsed[1].pump)
    }

    @Test
    fun `treats blank and null enrichment properties as absent`() {
        val json = collection(
            feature(
                type = "REFILL",
                extraProperties = "\"pump\":\"\",\"operator\":null"
            )
        )

        val parsed = WaterSourceParser.parse(json).single()

        assertEquals(null, parsed.pump)
        assertEquals(null, parsed.operatorName)
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
