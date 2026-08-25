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
)

data class GeoJsonGeometry(
    val type: String,
    val coordinates: com.google.gson.JsonElement
)
