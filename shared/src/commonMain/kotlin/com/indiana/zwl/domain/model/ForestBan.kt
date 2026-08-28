package com.indiana.zwl.domain.model

data class ForestBan(
    val id: Long,
    val remoteId: Long,
    val forestDistrictCode: String?,
    val forestDistrictName: String,
    val rdlpName: String?,
    val forestryName: String?,
    val forestryCode: Int?,
    val reason: String,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val forestAddress: String?,
    val compartmentCode: String?,
    val areaSqMeters: Double?,
    val geometryWkt: String
)
