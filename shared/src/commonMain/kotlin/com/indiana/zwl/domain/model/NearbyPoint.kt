package com.indiana.zwl.domain.model

/** Type of a row in the "Pobliskie punkty" section of a point detail view. */
enum class NearbyPointType {
    OWN_POINT,
    POI,
    WATER_SOURCE
}

/**
 * One row of the nearby-points section: a saved point, POI or water source
 * within the configured radius of the viewed point, with the great-circle
 * distance to it already computed.
 *
 * [subtitle] carries the category label for POI (e.g. "Wiaty i schronienia")
 * and the type label for water sources (e.g. "Studnia"); it is empty for
 * [NearbyPointType.OWN_POINT], whose label ("Mój punkt") is UI text and stays
 * on the platforms.
 */
data class NearbyPoint(
    val type: NearbyPointType,
    val id: Long,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)
