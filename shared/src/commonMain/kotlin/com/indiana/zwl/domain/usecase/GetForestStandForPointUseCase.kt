package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.Zone

/**
 * Forest-stand card for an arbitrary point (saved-point properties parity
 * with the zone detail sheet). The underlying [GetForestStandUseCase] is
 * keyed by a zone-geometry bbox, so we synthesize a small square (~1 km
 * across) around the point and let BDL resolve the nadleśnictwo/region and
 * aggregate whatever stands fall inside.
 *
 * The SILP soil type (typ gleby) and ground cover (pokrywa) of the wydzielenie
 * under the point are fetched additionally and merged into the summary, so a
 * single cached JSON carries the whole card.
 */
class GetForestStandForPointUseCase(
    private val getForestStandUseCase: GetForestStandUseCase,
    private val getSoilCoverForPointUseCase: GetSoilCoverForPointUseCase
) {

    suspend operator fun invoke(latitude: Double, longitude: Double): Result<ForestStandSummary> {
        val standResult = getForestStandUseCase(zoneForPoint(latitude, longitude))
        if (standResult.isFailure) return standResult
        val stand = standResult.getOrDefault(emptySummary())
        val soilCover = try {
            getSoilCoverForPointUseCase(latitude, longitude)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return Result.success(
            stand.copy(
                soilType = soilCover?.soilType ?: stand.soilType,
                groundCover = soilCover?.groundCover ?: stand.groundCover
            )
        )
    }

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

        private fun emptySummary(): ForestStandSummary = ForestStandSummary(
            speciesBreakdown = emptyList(),
            forestFunction = null,
            standStructure = null,
            siteType = null,
            protectionCategory = null,
            totalAreaHa = 0.0,
            rotationAge = null
        )
    }
}
