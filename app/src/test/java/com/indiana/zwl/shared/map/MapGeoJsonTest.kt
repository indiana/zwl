package com.indiana.zwl.shared.map

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.shared.data.remote.GeoJsonToWkt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeoJsonTest {

    private val wktPolygon =
        "POLYGON ((19.123 52.123, 19.124 52.123, 19.124 52.124, 19.123 52.123))"

    private val wktMultiPolygon =
        "MULTIPOLYGON (((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0)), ((20.0 53.0, 20.1 53.0, 20.1 53.1, 20.0 53.0)))"

    @Test
    fun `zonesToGeoJson produces parseable valid JSON`() {
        val zones = listOf(
            Zone(
                id = 0,
                forestDistrict = "Nadleśnictwo Spychowo",
                geometryWkt = wktMultiPolygon,
                websiteUrl = "https://example.com"
            )
        )

        val json = MapGeoJson.zonesToGeoJson(zones)
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals("FeatureCollection", root["type"]?.jsonPrimitive?.content)
        val features = root["features"]?.jsonArray ?: throw AssertionError("no features")
        assertEquals(1, features.size)

        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("MultiPolygon", geometry["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `bansToGeoJson produces parseable valid JSON`() {
        val bans = listOf(
            ForestBan(
                id = 0,
                remoteId = 123L,
                forestDistrictCode = "X",
                forestDistrictName = "Nadleśnictwo Kudypy",
                rdlpName = null,
                forestryName = null,
                forestryCode = null,
                reason = "Okresowa ochrona lasu",
                description = null,
                startDate = null,
                endDate = null,
                forestAddress = null,
                compartmentCode = null,
                areaSqMeters = null,
                geometryWkt = wktPolygon
            )
        )

        val json = MapGeoJson.bansToGeoJson(bans)
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals("FeatureCollection", root["type"]?.jsonPrimitive?.content)
        val features = root["features"]?.jsonArray ?: throw AssertionError("no features")
        assertEquals(1, features.size)

        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("Polygon", geometry["type"]?.jsonPrimitive?.content)
        assertTrue(features[0].jsonObject["properties"]!!.jsonObject.containsKey("remoteId"))
    }

    @Test
    fun `poisToGeoJson produces parseable valid JSON`() {
        val pois = listOf(
            Poi(
                id = 0,
                code = "P1",
                description = "Wiata",
                name = "Wiata leśna",
                latitude = 52.5,
                longitude = 20.5
            )
        )

        val json = MapGeoJson.poisToGeoJson(pois)
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals("FeatureCollection", root["type"]?.jsonPrimitive?.content)
        val features = root["features"]?.jsonArray ?: throw AssertionError("no features")
        assertEquals(1, features.size)

        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("Point", geometry["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `bansToGeoJson handles polygon with holes`() {
        val wktWithHole =
            "POLYGON ((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0), (19.02 52.02, 19.03 52.02, 19.03 52.03, 19.02 52.02))"
        val bans = listOf(
            ForestBan(
                id = 1,
                remoteId = 2L,
                forestDistrictCode = "X",
                forestDistrictName = "Nadleśnictwo Kudypy",
                rdlpName = null,
                forestryName = null,
                forestryCode = null,
                reason = "Test",
                description = null,
                startDate = null,
                endDate = null,
                forestAddress = null,
                compartmentCode = null,
                areaSqMeters = null,
                geometryWkt = wktWithHole
            )
        )

        val json = MapGeoJson.bansToGeoJson(bans)
        val root = Json.parseToJsonElement(json).jsonObject

        val features = root["features"]?.jsonArray ?: throw AssertionError("no features")
        assertEquals(1, features.size)
        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("Polygon", geometry["type"]?.jsonPrimitive?.content)
        assertEquals(2, geometry["coordinates"]!!.jsonArray.size)
    }

    @Test
    fun `zonesToGeoJson handles multipolygon with holes`() {
        val wktWithHole =
            "MULTIPOLYGON (((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0), (19.02 52.02, 19.03 52.02, 19.03 52.03, 19.02 52.02)))"
        val zones = listOf(
            Zone(
                id = 1,
                forestDistrict = "Nadleśnictwo Spychowo",
                geometryWkt = wktWithHole,
                websiteUrl = null
            )
        )

        val json = MapGeoJson.zonesToGeoJson(zones)
        val root = Json.parseToJsonElement(json).jsonObject

        val features = root["features"]?.jsonArray ?: throw AssertionError("no features")
        assertEquals(1, features.size)
        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("MultiPolygon", geometry["type"]?.jsonPrimitive?.content)
        val polygons = geometry["coordinates"]!!.jsonArray
        assertEquals(1, polygons.size)
        assertEquals(2, polygons[0].jsonArray.size)
    }

    @Test
    fun `WktToGeoJson round-trips GeoJsonToWkt output`() {
        val cases = listOf(
            "POLYGON ((19.123 52.123, 19.124 52.123, 19.124 52.124, 19.123 52.123))" to "Polygon",
            "POLYGON ((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0), (19.02 52.02, 19.03 52.02, 19.03 52.03, 19.02 52.02))" to "Polygon",
            "MULTIPOLYGON (((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0)), ((20.0 53.0, 20.1 53.0, 20.1 53.1, 20.0 53.0)))" to "MultiPolygon"
        )

        for ((wkt, expectedType) in cases) {
            val geometry = WktToGeoJson.geometryToGeoJson(wkt)
            assertTrue("WKT should parse: $wkt", geometry != null)
            val reparsed = GeoJsonToWkt.geometryToWkt(geometry!!)
            assertTrue("WKT should round-trip: $wkt", reparsed != null)
            val reparsedGeometry = WktToGeoJson.geometryToGeoJson(reparsed!!)
            assertTrue("Reparsed WKT should parse: $reparsed", reparsedGeometry != null)
            assertEquals(expectedType, reparsedGeometry!!.type)
            assertEquals(geometry.coordinates, reparsedGeometry.coordinates)
        }
    }
}