package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.remote.BdlFireApi
import com.indiana.zwl.domain.model.Location
import kotlinx.coroutines.CancellationException

class GetFireRiskUseCase(
    private val fireApi: BdlFireApi
) {
    suspend operator fun invoke(location: Location): Result<Int> {
        return try {
            val geometry = "${location.longitude},${location.latitude}"
            val response = fireApi.getFireHazard(geometry = geometry)
            val code = response.features?.firstOrNull()?.properties?.kodInt ?: -1
            Result.success(code)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
