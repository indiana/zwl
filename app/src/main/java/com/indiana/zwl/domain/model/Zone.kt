package com.indiana.zwl.domain.model

data class Zone(
    val id: Long,
    val forestDistrict: String,
    val geometryWkt: String,
    val fireRiskLevel: Int? = null,
    val fireRiskTimestamp: Long? = null,
    val forestStandJson: String? = null,
    val forestStandTimestamp: Long? = null,
    val websiteUrl: String? = null
)
