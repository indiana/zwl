package com.indiana.zwl.domain.usecase

import com.indiana.zwl.shared.data.remote.BdlFireApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class GetFireRiskUseCase @Inject constructor(
    private val fireApi: BdlFireApi
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): Result<Int> {
        return try {
            val geometry = "$longitude,$latitude"
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
