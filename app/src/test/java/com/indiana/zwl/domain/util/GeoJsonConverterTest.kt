package com.indiana.zwl.domain.util

import com.indiana.zwl.shared.data.remote.GeoJsonToWkt
import com.indiana.zwl.shared.data.remote.model.GeoJsonGeometry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoJsonConverterTest {

    @Test
    fun `geometryToWkt should convert Polygon correctly`() {
        val geometry = GeoJsonGeometry(
            type = "Polygon",
            coordinates = JsonArray(listOf(
                JsonArray(listOf(
                    JsonArray(listOf(JsonPrimitive(19.123), JsonPrimitive(52.123))),
                    JsonArray(listOf(JsonPrimitive(19.124), JsonPrimitive(52.123))),
                    JsonArray(listOf(JsonPrimitive(19.124), JsonPrimitive(52.124))),
                    JsonArray(listOf(JsonPrimitive(19.123), JsonPrimitive(52.123)))
                ))
            ))
        )

        val wkt = GeoJsonToWkt.geometryToWkt(geometry)

        assertEquals("POLYGON ((19.123 52.123, 19.124 52.123, 19.124 52.124, 19.123 52.123))", wkt)
    }

    @Test
    fun `geometryToWkt should convert MultiPolygon correctly`() {
        val geometry = GeoJsonGeometry(
            type = "MultiPolygon",
            coordinates = JsonArray(listOf(
                JsonArray(listOf(
                    JsonArray(listOf(
                        JsonArray(listOf(JsonPrimitive(19.0), JsonPrimitive(52.0))),
                        JsonArray(listOf(JsonPrimitive(19.1), JsonPrimitive(52.0))),
                        JsonArray(listOf(JsonPrimitive(19.1), JsonPrimitive(52.1))),
                        JsonArray(listOf(JsonPrimitive(19.0), JsonPrimitive(52.0)))
                    ))
                )),
                JsonArray(listOf(
                    JsonArray(listOf(
                        JsonArray(listOf(JsonPrimitive(20.0), JsonPrimitive(53.0))),
                        JsonArray(listOf(JsonPrimitive(20.1), JsonPrimitive(53.0))),
                        JsonArray(listOf(JsonPrimitive(20.1), JsonPrimitive(53.1))),
                        JsonArray(listOf(JsonPrimitive(20.0), JsonPrimitive(53.0)))
                    ))
                ))
            ))
        )

        val wkt = GeoJsonToWkt.geometryToWkt(geometry)

        assertEquals(
            "MULTIPOLYGON (((19.0 52.0, 19.1 52.0, 19.1 52.1, 19.0 52.0)), ((20.0 53.0, 20.1 53.0, 20.1 53.1, 20.0 53.0)))",
            wkt
        )
    }

    @Test
    fun `geometryToWkt should return null for unsupported geometry type`() {
        val geometry = GeoJsonGeometry(
            type = "LineString",
            coordinates = JsonArray(listOf(
                JsonArray(listOf(JsonPrimitive(19.0), JsonPrimitive(52.0))),
                JsonArray(listOf(JsonPrimitive(19.1), JsonPrimitive(52.1)))
            ))
        )

        val wkt = GeoJsonToWkt.geometryToWkt(geometry)

        assertNull(wkt)
    }

    @Test
    fun `extractForestDistrict should extract from link correct format`() {
        val props = mapOf("link" to "kudypy.lasy.gov.pl")
        val district = GeoJsonToWkt.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Kudypy", district)
    }

    @Test
    fun `extractForestDistrict should fallback to other keys if link empty`() {
        val props = mapOf(
            "link" to "",
            "nadlesnictwo" to "Nadleśnictwo Spychowo"
        )
        val district = GeoJsonToWkt.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Spychowo", district)
    }

    @Test
    fun `extractForestDistrict should fallback to nzw_ob`() {
        val props = mapOf(
            "nzw_ob" to "Nadleśnictwo Jedwabno"
        )
        val district = GeoJsonToWkt.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Jedwabno", district)
    }

    @Test
    fun `extractForestDistrict should return unknown fallback`() {
        val props = emptyMap<String, String>()
        val district = GeoJsonToWkt.extractForestDistrict(props)
        assertEquals("Nadleśnictwo (Nieznane)", district)
    }

    @Test
    fun `extractWebsiteUrl returns https root for bare host`() {
        val url = GeoJsonToWkt.extractWebsiteUrl(mapOf("link" to "kudypy.szczecinek.lasy.gov.pl"))
        assertEquals("https://kudypy.szczecinek.lasy.gov.pl", url)
    }

    @Test
    fun `extractWebsiteUrl strips path from full link`() {
        val url = GeoJsonToWkt.extractWebsiteUrl(
            mapOf("link" to "https://kaliska.gdansk.lasy.gov.pl/program-zanocuj-w-lesie-")
        )
        assertEquals("https://kaliska.gdansk.lasy.gov.pl", url)
    }

    @Test
    fun `extractWebsiteUrl returns null when link missing or invalid`() {
        assertNull(GeoJsonToWkt.extractWebsiteUrl(emptyMap()))
        assertNull(GeoJsonToWkt.extractWebsiteUrl(mapOf("link" to "")))
        assertNull(GeoJsonToWkt.extractWebsiteUrl(mapOf("link" to "not a valid url")))
    }
}
