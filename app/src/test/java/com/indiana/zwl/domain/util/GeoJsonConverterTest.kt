package com.indiana.zwl.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.MultiPolygon
import java.io.StringReader

class GeoJsonConverterTest {

    @Test
    fun `parseFeatureCollectionStream should parse Polygon correctly`() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "link": "kudypy.szczecinek.lasy.gov.pl",
                    "nzw_ob": "Kudypy"
                  },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [
                        [19.123, 52.123],
                        [19.124, 52.123],
                        [19.124, 52.124],
                        [19.123, 52.123]
                      ]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedProperties = mutableListOf<Map<String, String>>()
        val parsedGeometries = mutableListOf<org.locationtech.jts.geom.Geometry>()

        GeoJsonConverter.parseFeatureCollectionStream(StringReader(geoJson)) { properties, geometry ->
            parsedProperties.add(properties)
            parsedGeometries.add(geometry)
        }

        assertEquals(1, parsedProperties.size)
        assertEquals("kudypy.szczecinek.lasy.gov.pl", parsedProperties[0]["link"])
        assertEquals("Kudypy", parsedProperties[0]["nzw_ob"])

        assertEquals(1, parsedGeometries.size)
        assertTrue(parsedGeometries[0] is Polygon)
        val polygon = parsedGeometries[0] as Polygon
        assertEquals(4, polygon.exteriorRing.numPoints)
        assertEquals(19.123, polygon.exteriorRing.coordinates[0].x, 0.0001)
        assertEquals(52.123, polygon.exteriorRing.coordinates[0].y, 0.0001)
    }

    @Test
    fun `parseFeatureCollectionStream should parse MultiPolygon correctly`() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "nzw_ob": "MultiZone"
                  },
                  "geometry": {
                    "type": "MultiPolygon",
                    "coordinates": [
                      [
                        [
                          [19.0, 52.0],
                          [19.1, 52.0],
                          [19.1, 52.1],
                          [19.0, 52.0]
                        ]
                      ],
                      [
                        [
                          [20.0, 53.0],
                          [20.1, 53.0],
                          [20.1, 53.1],
                          [20.0, 53.0]
                        ]
                      ]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedProperties = mutableListOf<Map<String, String>>()
        val parsedGeometries = mutableListOf<org.locationtech.jts.geom.Geometry>()

        GeoJsonConverter.parseFeatureCollectionStream(StringReader(geoJson)) { properties, geometry ->
            parsedProperties.add(properties)
            parsedGeometries.add(geometry)
        }

        assertEquals(1, parsedProperties.size)
        assertEquals("MultiZone", parsedProperties[0]["nzw_ob"])

        assertEquals(1, parsedGeometries.size)
        assertTrue(parsedGeometries[0] is MultiPolygon)
        val multiPolygon = parsedGeometries[0] as MultiPolygon
        assertEquals(2, multiPolygon.numGeometries)
    }

    @Test
    fun `extractForestDistrict should extract from link correct format`() {
        val props = mapOf("link" to "kudypy.lasy.gov.pl")
        val district = GeoJsonConverter.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Kudypy", district)
    }

    @Test
    fun `extractForestDistrict should fallback to other keys if link empty`() {
        val props = mapOf(
            "link" to "",
            "nadlesnictwo" to "Nadleśnictwo Spychowo"
        )
        val district = GeoJsonConverter.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Spychowo", district)
    }

    @Test
    fun `extractForestDistrict should fallback to nzw_ob`() {
        val props = mapOf(
            "nzw_ob" to "Nadleśnictwo Jedwabno"
        )
        val district = GeoJsonConverter.extractForestDistrict(props)
        assertEquals("Nadleśnictwo Jedwabno", district)
    }

    @Test
    fun `extractForestDistrict should return unknown fallback`() {
        val props = emptyMap<String, String>()
        val district = GeoJsonConverter.extractForestDistrict(props)
        assertEquals("Nadleśnictwo (Nieznane)", district)
    }
}
