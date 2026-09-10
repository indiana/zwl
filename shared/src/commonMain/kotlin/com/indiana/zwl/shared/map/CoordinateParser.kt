package com.indiana.zwl.shared.map

/** Latitude/longitude pair parsed from arbitrary user input. */
data class GeoCoordinate(val latitude: Double, val longitude: Double)

/**
 * Extracts a coordinate pair from pasted text.
 *
 * Accepted forms, in priority order:
 *  1. Only coordinates (tolerant separators/decimals), e.g.
 *     "52.123456, 21.123456" or "52,123456; 21,123456".
 *  2. Map links (Google/Apple), e.g. "?q=52.3,21.7", "?ll=...", "@52.3,21.7"
 *     or Google place data "!3d52.3!4d21.7".
 *  3. Coordinates embedded in prose, e.g.
 *     "Twoje współrzędne to 52.345723,21.735486 Zobacz co tam jest ciekawego."
 *
 * Shortened links (maps.app.goo.gl) cannot be resolved without a network call
 * and are therefore not supported.
 */
object CoordinateParser {

    private const val DEC = """-?\d{1,3}\.\d{2,}"""
    private const val DEC_URL = """-?\d{1,3}\.\d+"""

    private val legacyWhole = Regex(
        """\s*([-]?\d+(?:[.,]\d+)?)\s*[,;\s]\s*([-]?\d+(?:[.,]\d+)?)\s*"""
    )

    // Google Maps place data: "...!3d52.345723!4d21.735486..."
    private val urlBang = Regex("""!3d($DEC_URL)!4d($DEC_URL)""", RegexOption.IGNORE_CASE)

    // Query params: ?q=, ?query=, ?ll=, ?daddr=, ?saddr=, ?destination=, ?center=
    private val urlParam = Regex(
        """[?&#](?:q|query|ll|daddr|saddr|destination|center)=(?:loc:)?($DEC_URL)\s*,\s*($DEC_URL)""",
        RegexOption.IGNORE_CASE
    )

    // In-link camera anchor: "@52.345723,21.735486,15z"
    private val urlAt = Regex("""@($DEC_URL)\s*,\s*($DEC_URL)""")

    // Prose coordinates with dot decimals: "52.345723,21.735486"
    private val embeddedDot = Regex("""(?<![\d.])($DEC)\s*,\s*($DEC)""")

    // Prose coordinates with Polish comma decimals: "52,345723; 21,735486"
    private val embeddedComma = Regex(
        """(?<![\d,.])(-?\d{1,3},\d{3,})\s*[;\s]\s*(-?\d{1,3},\d{3,})"""
    )

    fun parse(input: String): GeoCoordinate? {
        val text = input.trim()
        if (text.isEmpty()) return null

        parseLegacyWhole(text)?.let { return it }
        parseUrl(text)?.let { return it }
        parseEmbedded(text)?.let { return it }
        return null
    }

    private fun parseLegacyWhole(text: String): GeoCoordinate? {
        val match = legacyWhole.matchEntire(text) ?: return null
        return coordinate(
            match.groupValues[1].replace(',', '.'),
            match.groupValues[2].replace(',', '.')
        )
    }

    private fun parseUrl(text: String): GeoCoordinate? {
        for (pattern in listOf(urlBang, urlParam, urlAt)) {
            parseWith(text, pattern, normalizeComma = false)?.let { return it }
        }
        return null
    }

    private fun parseEmbedded(text: String): GeoCoordinate? {
        parseWith(text, embeddedDot, normalizeComma = false)?.let { return it }
        parseWith(text, embeddedComma, normalizeComma = true)?.let { return it }
        return null
    }

    /**
     * Returns the first valid coordinate pair found by [pattern], skipping
     * structurally matching but out-of-range candidates.
     */
    private fun parseWith(
        text: String,
        pattern: Regex,
        normalizeComma: Boolean
    ): GeoCoordinate? {
        var start = 0
        while (start <= text.length) {
            val match = pattern.find(text, start) ?: return null
            val first = match.groupValues[1]
            val second = match.groupValues[2]
            val coordinate = if (normalizeComma) {
                coordinate(first.replace(',', '.'), second.replace(',', '.'))
            } else {
                coordinate(first, second)
            }
            if (coordinate != null) return coordinate
            start = match.range.last + 1
        }
        return null
    }

    private fun coordinate(latRaw: String, lngRaw: String): GeoCoordinate? {
        val lat = latRaw.toDoubleOrNull() ?: return null
        val lng = lngRaw.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoCoordinate(lat, lng)
    }
}
