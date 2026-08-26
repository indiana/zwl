package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeoJsonCollection(
    val type: String,
    val features: List<GeoJsonFeature>
)

@Serializable
data class GeoJsonFeature(
    val type: String,
    val properties: Map<String, JsonElement?>? = null,
    val geometry: GeoJsonGeometry
)

@Serializable
data class GeoJsonGeometry(
    val type: String,
    val coordinates: JsonElement
)
