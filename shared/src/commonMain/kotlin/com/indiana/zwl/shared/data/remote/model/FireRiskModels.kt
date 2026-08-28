package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class FireRiskGeoJson(
    val features: List<FireRiskFeature>?
)

@Serializable
data class FireRiskFeature(
    val properties: FireRiskProperties
)

@Serializable
data class FireRiskProperties(
    val kod: Double? = null,
    val opis: String? = null
) {
    val kodInt: Int? get() = kod?.toInt()
}
