package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.Zone

/**
 * Forest-stand card for an arbitrary point (saved-point properties parity
 * with the zone detail sheet). The underlying [GetForestStandUseCase] is
 * keyed by a zone-geometry bbox, so we synthesize a small square (~1 km
 * across) around the point and let BDL resolve the nadleśnictwo/region and
 * aggregate whatever stands fall inside.
 */
class GetForestStandForPointUseCase(
    private val getForestStandUseCase: GetForestStandUseCase
) {

    suspend operator fun invoke(latitude: Double, longitude: Double): Result<ForestStandSummary> =
        getForestStandUseCase(zoneForPoint(latitude, longitude))

    companion object {
        // ±0.005° ≈ 550 m; bbox spans ~1.1 km. Small enough to stay local to
        // the point, large enough to catch surrounding stands for species mix.
        private const val BBOX_HALF_DEGREES = 0.005

        fun zoneForPoint(latitude: Double, longitude: Double): Zone {
            val half = BBOX_HALF_DEGREES
            val west = longitude - half
            val east = longitude + half
            val south = latitude - half
            val north = latitude + half
            val wkt = "POLYGON(($west $south, $east $south, $east $north, $west $north, $west $south))"
            return Zone(id = 0, forestDistrict = "", geometryWkt = wkt)
        }
    }
}
