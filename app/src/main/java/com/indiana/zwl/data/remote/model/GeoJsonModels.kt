package com.indiana.zwl.data.remote.model

import com.google.gson.annotations.SerializedName

data class GeoJsonCollection(
    val type: String,
    val features: List<GeoJsonFeature>
)

data class GeoJsonFeature(
    val type: String,
    val properties: Map<String, Any?>?,
    val geometry: GeoJsonGeometry
) {
    val forestDistrict: String
        get() {
            val linkValue = properties?.get("link") as? String
            if (!linkValue.isNullOrBlank()) {
                try {
                    val urlStr = if (!linkValue.startsWith("http://") && !linkValue.startsWith("https://")) {
                        "https://$linkValue"
                    } else {
                        linkValue
                    }
                    val uri = java.net.URI(urlStr)
                    val host = uri.host ?: ""
                    val parts = host.split(".")
                    val namePart = parts.firstOrNull { it != "www" && it.isNotBlank() }
                    if (!namePart.isNullOrBlank()) {
                        return "Nadleśnictwo " + namePart.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val keys = listOf("nadlesnictw", "nadlesnictwo", "nazwa_nadl", "nazwa", "nadl", "district", "nzw_ob")
            for (key in keys) {
                val value = properties?.get(key)
                if (value is String && value.isNotBlank()) return value
            }
            return "Nadleśnictwo (Nieznane)"
        }
}

data class GeoJsonGeometry(
    val type: String,
    val coordinates: com.google.gson.JsonElement
)
