package com.indiana.zwl.domain.usecase

import android.location.Location
import com.indiana.zwl.data.remote.BdlFireApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class GetFireRiskUseCase @Inject constructor(
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
