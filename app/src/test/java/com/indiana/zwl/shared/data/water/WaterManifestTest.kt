package com.indiana.zwl.shared.data.water

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val valid = """
        {
          "version": 20260928,
          "generatedAt": "2026-09-28T03:12:44Z",
          "file": "water-20260928.geojson",
          "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          "bytes": 812345,
          "count": 8123,
          "sources": ["OSM"]
        }
    """.trimIndent()

    @Test
    fun `parses a valid manifest`() {
        val manifest = json.decodeFromString<WaterManifest>(valid)

        assertEquals(20260928L, manifest.version)
        assertEquals("water-20260928.geojson", manifest.file)
        assertEquals(812345L, manifest.bytes)
        assertEquals(8123L, manifest.count)
        assertEquals(listOf("OSM"), manifest.sources)
    }

    @Test
    fun `sources default to empty when absent`() {
        val withoutSources = """
            {"version":1,"generatedAt":"x","file":"a.geojson","sha256":"ab","bytes":1,"count":0}
        """.trimIndent()

        val manifest = json.decodeFromString<WaterManifest>(withoutSources)

        assertTrue(manifest.sources.isEmpty())
    }

    @Test
    fun `missing required field fails`() {
        val missingVersion = """
            {"generatedAt":"x","file":"a.geojson","sha256":"ab","bytes":1,"count":0}
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<WaterManifest>(missingVersion)
        }
    }

    @Test
    fun `version comparison follows numeric order`() {
        val older = json.decodeFromString<WaterManifest>(
            """{"version":20260901,"generatedAt":"x","file":"a.geojson","sha256":"ab","bytes":1,"count":0}"""
        )
        val newer = json.decodeFromString<WaterManifest>(
            """{"version":20260928,"generatedAt":"x","file":"a.geojson","sha256":"ab","bytes":1,"count":0}"""
        )

        assertTrue(newer.version > older.version)
    }
}
