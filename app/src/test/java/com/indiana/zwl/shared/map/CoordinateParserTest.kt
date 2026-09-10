package com.indiana.zwl.shared.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    private fun assertCoords(expectedLat: Double, expectedLng: Double, input: String) {
        val parsed = CoordinateParser.parse(input)
        assertEquals("lat for <$input>", expectedLat, parsed!!.latitude, 1e-9)
        assertEquals("lng for <$input>", expectedLng, parsed.longitude, 1e-9)
    }

    @Test
    fun `coordinates embedded in prose`() {
        assertCoords(
            52.345723, 21.735486,
            "Twoje współrzędne to 52.345723,21.735486 Zobacz co tam jest ciekawego."
        )
    }

    @Test
    fun `coordinates with space after comma in prose`() {
        assertCoords(52.345723, 21.735486, "Punkt: 52.345723, 21.735486 - zapraszam")
    }

    @Test
    fun `trailing sentence period is ignored`() {
        assertCoords(52.345723, 21.735486, "Współrzędne: 52.345723,21.735486.")
    }

    @Test
    fun `plain coordinates`() {
        assertCoords(52.123456, 21.123456, "52.123456, 21.123456")
    }

    @Test
    fun `plain coordinates comma decimal and semicolon`() {
        assertCoords(52.123456, 21.123456, "52,123456; 21,123456")
    }

    @Test
    fun `negative coordinates`() {
        assertCoords(-33.865143, 151.209900, "-33.865143,151.209900")
    }

    @Test
    fun `first valid pair wins`() {
        assertCoords(
            52.111111, 21.111111,
            "pierwszy 52.111111,21.111111 drugi 53.222222,22.222222"
        )
    }

    @Test
    fun `out of range is rejected`() {
        assertNull(CoordinateParser.parse("300.123456,21.735486"))
    }

    @Test
    fun `text without coordinates returns null`() {
        assertNull(CoordinateParser.parse("Brak tu jakichkolwiek współrzędnych."))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(CoordinateParser.parse("   "))
    }

    @Test
    fun `google maps query link`() {
        assertCoords(
            52.345723, 21.735486,
            "https://www.google.com/maps?q=52.345723,21.735486"
        )
    }

    @Test
    fun `google maps at link`() {
        assertCoords(
            52.345723, 21.735486,
            "https://www.google.com/maps/@52.345723,21.735486,15z"
        )
    }

    @Test
    fun `google maps place data link`() {
        assertCoords(
            52.345723, 21.735486,
            "https://www.google.com/maps/place/Foo/data=!3m1!4b1!4m5!3m4!1s0x0:0x0!8m2!3d52.345723!4d21.735486"
        )
    }

    @Test
    fun `apple maps ll link`() {
        assertCoords(
            52.345723, 21.735486,
            "https://maps.apple.com/?ll=52.345723,21.735486"
        )
    }

    @Test
    fun `geo uri`() {
        assertCoords(52.345723, 21.735486, "geo:52.345723,21.735486?z=15")
    }
}
