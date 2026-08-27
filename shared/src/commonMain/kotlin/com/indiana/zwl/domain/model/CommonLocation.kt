package com.indiana.zwl.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val time: Long = 0L
) {
    fun distanceTo(other: Location): Double {
        val lat1 = latitude * PI / 180.0
        val lat2 = other.latitude * PI / 180.0
        val dLat = (other.latitude - latitude) * PI / 180.0
        val dLon = (other.longitude - longitude) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingTo(other: Location): Float {
        val lat1 = latitude * PI / 180.0
        val lat2 = other.latitude * PI / 180.0
        val dLon = (other.longitude - longitude) * PI / 180.0
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) -
                sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
        return bearing.toFloat()
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}