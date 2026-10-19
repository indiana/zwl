package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Location
import com.indiana.zwl.domain.model.NearbyPoint
import com.indiana.zwl.domain.model.NearbyPointType
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.model.WaterSource
import com.indiana.zwl.domain.util.PoiUiGroup
import com.indiana.zwl.domain.util.WaterSourceGroup
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.domain.util.displayGroupName
import com.indiana.zwl.domain.util.displayLabel
import com.indiana.zwl.domain.util.uiGroup
import com.indiana.zwl.domain.util.waterGroup

/**
 * Result of a nearby-points query: the rows to show (capped at the limit) and
 * how many points the radius actually contains, so the UI can render a
 * "…i N więcej w promieniu" row without a second query.
 */
data class NearbyPointsResult(
    val points: List<NearbyPoint>,
    val totalCount: Int
)

/**
 * What the nearby-points section may contain. The visibility flags mirror the
 * map layer toggles, so the list shows exactly what the user sees on the map.
 * Callers that pass lists already filtered by those toggles (Android) leave
 * the flags at their defaults.
 */
data class NearbyFilter(
    val radiusKm: Float = DEFAULT_RADIUS_KM,
    val includeOwnPoints: Boolean = true,
    val visiblePoiGroups: List<PoiUiGroup> = PoiUiGroup.entries,
    val includeDrinking: Boolean = true,
    val includeSprings: Boolean = true,
    val includeWells: Boolean = true,
    val excludeSavedPointId: Long? = null,
    val excludePoiId: Long? = null,
    val excludeWaterSourceId: Long? = null
) {
    companion object {
        const val DEFAULT_RADIUS_KM = 5f
        const val MIN_RADIUS_KM = 0.5f
        const val MAX_RADIUS_KM = 25f
        const val RADIUS_STEP_KM = 0.5f
        const val DEFAULT_LIMIT = 10
    }
}

/**
 * Points of every type within [NearbyFilter.radiusKm] of the given coordinate,
 * sorted by distance ascending and capped at [limit].
 *
 * Pure and synchronous: callers own their lists (Android passes the ones the
 * map renders; iOS passes the cached ones from ForestApp), so this runs on any
 * dispatcher and is trivially testable. The viewed point itself is removed via
 * the filter's exclude ids.
 */
class GetNearbyPointsUseCase {

    operator fun invoke(
        latitude: Double,
        longitude: Double,
        savedPoints: List<SavedPoint>,
        pois: List<Poi>,
        waterSources: List<WaterSource>,
        filter: NearbyFilter = NearbyFilter(),
        limit: Int = NearbyFilter.DEFAULT_LIMIT
    ): NearbyPointsResult {
        val origin = Location(latitude, longitude)
        val radiusMeters = filter.radiusKm.toDouble() * 1000.0

        val candidates = ArrayList<NearbyPoint>()

        if (filter.includeOwnPoints) {
            for (point in savedPoints) {
                if (point.id == filter.excludeSavedPointId) continue
                val distance = Location(point.latitude, point.longitude).distanceTo(origin)
                if (distance <= radiusMeters) {
                    candidates += NearbyPoint(
                        type = NearbyPointType.OWN_POINT,
                        id = point.id,
                        name = point.name,
                        subtitle = "",
                        latitude = point.latitude,
                        longitude = point.longitude,
                        distanceMeters = distance
                    )
                }
            }
        }

        for (poi in pois) {
            if (poi.id == filter.excludePoiId) continue
            if (poi.classify().uiGroup() !in filter.visiblePoiGroups) continue
            val distance = Location(poi.latitude, poi.longitude).distanceTo(origin)
            if (distance <= radiusMeters) {
                candidates += NearbyPoint(
                    type = NearbyPointType.POI,
                    id = poi.id,
                    name = poi.name.ifBlank { poi.displayGroupName() },
                    subtitle = poi.displayGroupName(),
                    latitude = poi.latitude,
                    longitude = poi.longitude,
                    distanceMeters = distance
                )
            }
        }

        for (source in waterSources) {
            if (source.id == filter.excludeWaterSourceId) continue
            val included = when (source.type.waterGroup()) {
                WaterSourceGroup.DRINKING -> filter.includeDrinking
                WaterSourceGroup.SPRING -> filter.includeSprings
                WaterSourceGroup.WELL -> filter.includeWells
            }
            if (!included) continue
            val distance = Location(source.latitude, source.longitude).distanceTo(origin)
            if (distance <= radiusMeters) {
                candidates += NearbyPoint(
                    type = NearbyPointType.WATER_SOURCE,
                    id = source.id,
                    name = source.name.ifBlank { "Źródło wody" },
                    subtitle = source.type.displayLabel(),
                    latitude = source.latitude,
                    longitude = source.longitude,
                    distanceMeters = distance
                )
            }
        }

        val sorted = candidates.sortedWith(
            compareBy<NearbyPoint> { it.distanceMeters }
                .thenBy { it.type.ordinal }
                .thenBy { it.name }
                .thenBy { it.id }
        )
        return NearbyPointsResult(
            points = sorted.take(limit),
            totalCount = sorted.size
        )
    }
}
