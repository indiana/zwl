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
            upper.startsWith("MULTIPOLYGON") -> {
                val polygons = parsePolygonGroups(trimmed) ?: return null
                GeoJsonGeometry(
                    type = "MultiPolygon",
                    coordinates = JsonArray(polygons.map { polygon -> JsonArray(polygon.map { ring -> ring }) })
                )
            }
            upper.startsWith("POLYGON") -> {
                val rings = parsePolygonGroups(trimmed)?.firstOrNull() ?: return null
                GeoJsonGeometry(
                    type = "Polygon",
                    coordinates = JsonArray(rings.map { ring -> ring })
                )
            }
            else -> null
        }
    }

    /**
     * Parses the coordinate section of a POLYGON or MULTIPOLYGON WKT into a
     * list (polygons) of lists (rings) of JsonArrays ([lng, lat] pairs).
     */
    private fun parsePolygonGroups(wkt: String): List<List<JsonArray>>? {
        val openIndex = wkt.indexOf('(')
        val closeIndex = wkt.lastIndexOf(')')
        if (openIndex < 0 || closeIndex <= openIndex) return null

        val core = wkt.substring(openIndex + 1, closeIndex)
        val groups = splitTopLevel(core)
        if (groups.isEmpty()) return null

        val polygons = mutableListOf<List<JsonArray>>()
        for (group in groups) {
            val inner = group.removeSurrounding("(", ")")
            if (inner.isBlank()) continue
            val rings = splitTopLevel(inner).mapNotNull { ringToken -> ringToJsonArray(ringToken) }
            if (rings.isEmpty()) return null
            polygons.add(rings)
        }
        if (polygons.isEmpty()) return null
        return polygons
    }

    private fun ringToJsonArray(ringToken: String): JsonArray? {
        val inner = ringToken.trim().removeSurrounding("(", ")")
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