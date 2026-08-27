package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.ForestBanSyncParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncForestBansUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val forestBanRepository: ForestBanRepository
) {
    suspend operator fun invoke(): Result<List<ForestBan>> = withContext(Dispatchers.IO) {
        try {
            val allBans = mutableListOf<ForestBan>()
            var offset = 0
            val batchSize = 500
            var hasMore = true

            while (hasMore) {
                val collection = arcgisApi.getForestBans(resultOffset = offset, resultRecordCount = batchSize)
                val features = collection.features
                if (features.isEmpty()) break

                allBans.addAll(ForestBanSyncParser.parse(collection))

                if (features.size < batchSize) {
                    hasMore = false
                } else {
                    offset += batchSize
                }
            }

            if (allBans.isNotEmpty()) {
                forestBanRepository.clearAll()
                forestBanRepository.insertAll(allBans)
            }
            Result.success(allBans)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
