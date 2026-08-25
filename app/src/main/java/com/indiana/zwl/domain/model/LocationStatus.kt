package com.indiana.zwl.domain.model

sealed interface LocationStatus {
    data class InZone(val forestDistrict: String) : LocationStatus
    data class OutsideZone(
        val nearestDistrict: String,
        val distanceMeters: Double,
        val bearingDegrees: Float
    ) : LocationStatus
    data object EmptyData : LocationStatus
}
