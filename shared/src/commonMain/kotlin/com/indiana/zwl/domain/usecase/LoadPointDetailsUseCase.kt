package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestStandSummary
import kotlinx.coroutines.CancellationException

data class PointDetails(
    val fireRiskLevel: Int,
    val forestStand: ForestStandSummary?
)

/**
 * Fire-risk code + BDL forest stand (with SILP soil/ground cover already merged
 * by GetForestStandForPointUseCase) for an arbitrary coordinate. No persistence:
 * this is used for a pending (unsaved) point on Android and iOS.
 */
class LoadPointDetailsUseCase(
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getForestStandForPointUseCase: GetForestStandForPointUseCase
) {
    suspend operator fun invoke(latitude: Double, longitude: Double, now: Long): PointDetails {
        // No cache exists for an unsaved point -> caches are always null.
        val fireRiskLevel = getFireRiskUseCase(latitude, longitude, now)
        val forestStand = try {
            getForestStandForPointUseCase(latitude, longitude).getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        return PointDetails(fireRiskLevel, forestStand)
    }
}
