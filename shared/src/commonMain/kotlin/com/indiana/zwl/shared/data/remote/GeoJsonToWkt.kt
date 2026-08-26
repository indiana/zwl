package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.shared.data.remote.model.GeoJsonFeature
import com.indiana.zwl.shared.data.remote.model.GeoJsonGeometry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object GeoJsonToWkt {

    fun geometryToWkt(geometry: GeoJsonGeometry): String? {
        val coords = geometry.coordinates
        return when (geometry.type.lowercase()) {
            "polygon" -> polygonToWkt(coords.jsonArray)
            "multipolygon" -> multiPolygonToWkt(coords.jsonArray)
            else -> null
        }
    }

    private fun polygonToWkt(rings: JsonArray): String? {
        if (rings.isEmpty()) return null
        val shell = ringToWkt(rings[0].jsonArray) ?: return null
        val holes = (1 until rings.size).mapNotNull { ringToWkt(rings[it].jsonArray) }
        return if (holes.isEmpty()) {
            "POLYGON ($shell)"
        } else {
            "POLYGON ($shell, ${holes.joinToString(", ")})"
        }
    }

    private fun multiPolygonToWkt(polygons: JsonArray): String? {
        val wktPolygons = polygons.mapNotNull { polyArray ->
            val rings = polyArray.jsonArray
            if (rings.isEmpty()) return@mapNotNull null
            val shell = ringToWkt(rings[0].jsonArray) ?: return@mapNotNull null
            val holes = (1 until rings.size).mapNotNull { ringToWkt(rings[it].jsonArray) }
            if (holes.isEmpty()) "($shell)" else "($shell, ${holes.joinToString(", ")})"
        }
        if (wktPolygons.isEmpty()) return null
        return "MULTIPOLYGON (${wktPolygons.joinToString(", ")})"
    }

    private fun ringToWkt(coords: JsonArray): String? {
        if (coords.size < 4) return null
        val points = coords.map { coord ->
            val pair = coord.jsonArray
            "${pair[0].jsonPrimitive.double} ${pair[1].jsonPrimitive.double}"
        }
        return "(${points.joinToString(", ")})"
    }

    fun extractForestDistrict(properties: Map<String, String>): String {
        val linkValue = properties["link"]
        if (!linkValue.isNullOrBlank()) {
            try {
                val urlStr = if (!linkValue.startsWith("http://") && !linkValue.startsWith("https://")) {
                    "https://$linkValue"
                } else {
                    linkValue
                }
                val uri = urlStrToUri(urlStr)
                val host = uri?.second ?: ""
                val parts = host.split(".")
                val namePart = parts.firstOrNull { it != "www" && it.isNotBlank() }
                if (!namePart.isNullOrBlank()) {
                    return "Nadleśnictwo " + namePart.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
                }
            } catch (_: Exception) {
            }
        }

        val keys = listOf("nadlesnictw", "nadlesnictwo", "nazwa_nadl", "nazwa", "nadl", "district", "nzw_ob")
        for (key in keys) {
            val value = properties[key]
            if (!value.isNullOrBlank()) return value
        }
        return "Nadleśnictwo (Nieznane)"
    }

    fun extractWebsiteUrl(properties: Map<String, String>): String? {
        val linkValue = properties["link"]
        if (!linkValue.isNullOrBlank()) {
            try {
                val urlStr = if (!linkValue.startsWith("http://") && !linkValue.startsWith("https://")) {
                    "https://$linkValue"
                } else {
                    linkValue
                }
                val (_, host) = urlStrToUri(urlStr) ?: return null
                if (host.isNullOrBlank()) return null
                return "https://$host"
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun flattenCoords(coords: JsonArray): List<List<Double>> {
        val first = coords.getOrNull(0) ?: return emptyList()
        return if (first is JsonPrimitive) {
            listOf(coords.map { it.jsonPrimitive.double })
        } else {
            coords.flatMap { flattenCoords(it.jsonArray) }
        }
    }

    private fun urlStrToUri(urlStr: String): Pair<String?, String?> {
        val regex = Regex("^https?://([a-zA-Z0-9._-]+(?:\\.[a-zA-Z]{2,})+)")
        val match = regex.find(urlStr)
        return Pair(match?.groupValues?.get(1), match?.groupValues?.get(1))
    }
}
