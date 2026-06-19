package com.example.zwl.domain.model

sealed class LocationStatus {
    data class InZone(val forestDistrict: String) : LocationStatus()
    data class OutsideZone(
        val nearestDistrict: String,
        val distanceMeters: Double,
        val bearingDegrees: Float
    ) : LocationStatus()
    object EmptyData : LocationStatus()
}
