package com.example.zwl.data.remote.model

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
            val keys = listOf("nadlesnictw", "nadlesnictwo", "nazwa_nadl", "nazwa", "nadl", "district")
            for (key in keys) {
                val value = properties?.get(key)
                if (value is String) return value
            }
            return "Nieznane Nadleśnictwo"
        }
}

data class GeoJsonGeometry(
    val type: String,
    val coordinates: com.google.gson.JsonElement
)
