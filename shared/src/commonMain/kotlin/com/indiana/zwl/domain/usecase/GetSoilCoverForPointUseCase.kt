package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.SoilCover
import com.indiana.zwl.shared.data.remote.BdlStandDescriptionApi
import kotlinx.coroutines.CancellationException

/**
 * SILP soil type (typ gleby) and ground cover (pokrywa) for an arbitrary
 * point — resolves the wydzielenie under the point (PGL LP first, outside
 * PGL LP as fallback) and reads the BDL portal taxation description.
 * Null when the point has no forest subarea or the service fails.
 */
class GetSoilCoverForPointUseCase(
    private val standDescriptionApi: BdlStandDescriptionApi
) {

    suspend operator fun invoke(latitude: Double, longitude: Double): SoilCover? {
        return try {
            val subarea = standDescriptionApi.findSubarea(latitude, longitude) ?: return null
            standDescriptionApi.getSoilCover(subarea)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("GetSoilCoverForPointUseCase failed: ${e.message}")
            null
        }
    }
}
