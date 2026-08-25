package com.indiana.zwl.domain.model

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
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun bearingTo(other: Location): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
