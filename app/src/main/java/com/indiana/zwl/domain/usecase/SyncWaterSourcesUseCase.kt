package com.indiana.zwl.domain.usecase

import com.indiana.zwl.shared.data.water.WaterSyncManager
import com.indiana.zwl.shared.data.water.WaterSyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncWaterSourcesUseCase @Inject constructor(
    private val waterSyncManager: WaterSyncManager
) {
    suspend operator fun invoke(): Result<WaterSyncOutcome> = withContext(Dispatchers.IO) {
        try {
            waterSyncManager.refreshIfStale()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
