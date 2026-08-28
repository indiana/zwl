package com.indiana.zwl.shared.map

import com.indiana.zwl.shared.data.remote.model.GeoJsonGeometry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts WKT produced by [com.indiana.zwl.shared.data.remote.GeoJsonToWkt]
 * back into a GeoJSON geometry. A small dedicated parser is used instead of
 * the geometry library so it works identically on every target (JVM +
 * Kotlin/Native).
 */
object WktToGeoJson {

    fun geometryToGeoJson(wkt: String): GeoJsonGeometry? {
        val trimmed = wkt.trim()
        val upper = trimmed.uppercase()
        return when {
            upper.startsWith("MULTIPOLYGON") -> parseMultiPolygon(trimmed)
            upper.startsWith("POLYGON") -> parsePolygon(trimmed)
            else -> null
        }
    }

    /**
     * Parses a POLYGON WKT (`POLYGON ((x y, ...), (hole, ...))`) into a list
     * (rings) of JsonArrays ([lng, lat] pairs).
     */
    private fun parsePolygon(wkt: String): GeoJsonGeometry? {
        val core = parenCore(wkt) ?: return null
        val rings = splitTopLevel(core).mapNotNull { ringToken -> ringToJsonArray(ringToken) }
        if (rings.isEmpty()) return null
        return GeoJsonGeometry(
            type = "Polygon",
            coordinates = JsonArray(rings)
        )
    }

    /**
     * Parses a MULTIPOLYGON WKT (`MULTIPOLYGON (((x y, ...)), ((x y, ...)))`)
     * into a list (polygons) of lists (rings) of JsonArrays ([lng, lat] pairs).
     */
    private fun parseMultiPolygon(wkt: String): GeoJsonGeometry? {
        val core = parenCore(wkt) ?: return null
        val polygons = splitTopLevel(core).mapNotNull { polygonToken ->
            val inner = stripOuterParens(polygonToken) ?: return@mapNotNull null
            val rings = splitTopLevel(inner).mapNotNull { ringToken -> ringToJsonArray(ringToken) }
            if (rings.isEmpty()) null else rings
        }
        if (polygons.isEmpty()) return null
        return GeoJsonGeometry(
            type = "MultiPolygon",
            coordinates = JsonArray(polygons.map { polygon -> JsonArray(polygon) })
        )
    }

    /** Content between the outermost parenthesis pair of a WKT geometry. */
    private fun parenCore(wkt: String): String? {
        val openIndex = wkt.indexOf('(')
        val closeIndex = wkt.lastIndexOf(')')
        if (openIndex < 0 || closeIndex <= openIndex) return null
        return wkt.substring(openIndex + 1, closeIndex)
    }

    private fun stripOuterParens(token: String): String? {
        val t = token.trim()
        if (!t.startsWith("(") || !t.endsWith(")")) return null
        return t.substring(1, t.length - 1)
    }

    private fun ringToJsonArray(ringToken: String): JsonArray? {
        val inner = stripOuterParens(ringToken)?.trim() ?: return null
        if (inner.isBlank()) return null
        val coords = inner.split(",").mapNotNull { coordToken ->
            val parts = coordToken.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))
        }
        if (coords.size < 4) return null
        return JsonArray(coords)
    }

    /** Splits a string on commas that sit at parenthesis depth zero. */
    private fun splitTopLevel(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in content) {
            if (ch == '(') {
                depth++
            } else if (ch == ')') {
                depth--
            }
            if (ch == ',' && depth == 0) {
                val token = current.toString().trim()
                if (token.isNotEmpty()) result.add(token)
                current.clear()
            } else {
                current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) result.add(last)
        return result
    }
}