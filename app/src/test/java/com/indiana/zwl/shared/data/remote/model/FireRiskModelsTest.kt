package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FireRiskModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun `parses kod returned as a numeric string`() {
        val body = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":null,"properties":{"kod":"2","opis":"zagrozenie srednie"}}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromString<FireRiskGeoJson>(body)

        assertEquals(2, parsed.features?.firstOrNull()?.properties?.kodInt)
        assertEquals("zagrozenie srednie", parsed.features?.first()?.properties?.opis)
    }

    @Test
    fun `parses kod returned as a number`() {
        val body = """
            {"features":[{"properties":{"kod":3.0,"opis":"zagrozenie duze"}}]}
        """.trimIndent()

        val parsed = json.decodeFromString<FireRiskGeoJson>(body)

        assertEquals(3, parsed.features?.firstOrNull()?.properties?.kodInt)
    }

    @Test
    fun `parses negative kod string`() {
        val body = """
            {"features":[{"properties":{"kod":"-2","opis":"brak danych"}}]}
        """.trimIndent()

        val parsed = json.decodeFromString<FireRiskGeoJson>(body)

        assertEquals(-2, parsed.features?.firstOrNull()?.properties?.kodInt)
    }

    @Test
    fun `unparseable or missing kod maps to null`() {
        val body = """
            {"features":[{"properties":{"kod":"abc","opis":null}},{"properties":{"opis":"x"}}]}
        """.trimIndent()

        val parsed = json.decodeFromString<FireRiskGeoJson>(body)

        assertNull(parsed.features?.get(0)?.properties?.kodInt)
        assertNull(parsed.features?.get(1)?.properties?.kodInt)
    }
}
