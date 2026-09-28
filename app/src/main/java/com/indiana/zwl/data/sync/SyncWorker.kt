package com.indiana.zwl.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.indiana.zwl.domain.usecase.SyncForestBansUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncWaterSourcesUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncZonesUseCase: SyncZonesUseCase,
    private val syncPoiUseCase: SyncPoiUseCase,
    private val syncForestBansUseCase: SyncForestBansUseCase,
    private val syncWaterSourcesUseCase: SyncWaterSourcesUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            var zonesOk = false
            var poiOk = false
            var bansOk = false

            coroutineScope {
                val zonesDeferred = async { syncZonesUseCase() }
                val poiDeferred = async { syncPoiUseCase() }
                val bansDeferred = async { syncForestBansUseCase() }
                val waterDeferred = async { syncWaterSourcesUseCase() }

                zonesOk = zonesDeferred.await().isSuccess
                poiOk = poiDeferred.await().isSuccess
                bansOk = bansDeferred.await().isSuccess
                val waterResult = waterDeferred.await()
                if (waterResult.isFailure) {
                    waterResult.exceptionOrNull()?.printStackTrace()
                }
            }

            if (zonesOk && poiOk && bansOk) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}
