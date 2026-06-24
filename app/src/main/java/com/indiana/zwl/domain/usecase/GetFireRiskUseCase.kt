package com.indiana.zwl.domain.usecase

import android.location.Location
import com.indiana.zwl.data.remote.BdlFireApi
import javax.inject.Inject

class GetFireRiskUseCase @Inject constructor(
    private val fireApi: BdlFireApi
) {
    suspend operator fun invoke(location: Location): Result<Int> {
        return try {
            val geometry = "${location.longitude},${location.latitude}"
            val response = fireApi.getFireHazard(geometry = geometry)
            val code = response.features?.firstOrNull()?.properties?.kod ?: -1
            Result.success(code)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
