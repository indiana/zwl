package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.PoiSyncParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncPoiUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val poiRepository: PoiRepository
) {
    suspend operator fun invoke(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allPois = mutableListOf<Poi>()
            val layers = listOf(1, 2, 3, 4)

            for (layerId in layers) {
                var offset = 0
                var hasMore = true
                val recordCount = 2000

                while (hasMore) {
                    val response = arcgisApi.getTouristPoints(
                        layerId = layerId,
                        outFields = if (layerId == 4) "tur_edu_pnt_cd,tur_obj_desc,nzw_ob" else "tur_rec_pnt_cd,tur_obj_desc,nzw_ob",
                        resultOffset = offset,
                        resultRecordCount = recordCount
                    )

                    val features = response.features
                    if (features.isEmpty()) {
                        break
                    }

                    allPois.addAll(PoiSyncParser.parseFeatures(features))

                    if (features.size < recordCount) {
                        hasMore = false
                    } else {
                        offset += recordCount
                    }
                }
            }

            if (allPois.isNotEmpty()) {
                poiRepository.clearAll()
                poiRepository.insertAll(allPois)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Otrzymano pusta liste punktow turystycznych (POI) od API ArcGis."))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
