package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.domain.repository.PoiRepository
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
            val allEntities = mutableListOf<PoiEntity>()
            val layers = listOf(1, 2, 3, 4)

            for (layerId in layers) {
                var offset = 0
                var hasMore = true
                val recordCount = 2000
                val outFields = if (layerId == 4) "tur_edu_pnt_cd,tur_obj_desc,nzw_ob" else "tur_rec_pnt_cd,tur_obj_desc,nzw_ob"

                while (hasMore) {
                    val response = arcgisApi.getTouristPoints(
                        layerId = layerId,
                        outFields = outFields,
                        resultOffset = offset,
                        resultRecordCount = recordCount
                    )
                    
                    val features = response.features
                    if (features.isEmpty()) {
                        break
                    }

                    for (feature in features) {
                        val properties = feature.properties
                        val geom = feature.geometry
                        
                        if (geom.type.equals("point", ignoreCase = true) && geom.coordinates.isJsonArray) {
                            val coords = geom.coordinates.asJsonArray
                            if (coords.size() >= 2) {
                                val lon = coords.get(0).asDouble
                                val lat = coords.get(1).asDouble
                                
                                val code = properties?.get("tur_rec_pnt_cd")?.toString()
                                    ?: properties?.get("tur_edu_pnt_cd")?.toString()
                                    ?: ""
                                val desc = properties?.get("tur_obj_desc")?.toString() ?: ""
                                val name = properties?.get("nzw_ob")?.toString() ?: ""

                                allEntities.add(
                                    PoiEntity(
                                        code = code,
                                        description = desc,
                                        name = name,
                                        latitude = lat,
                                        longitude = lon
                                    )
                                )
                            }
                        }
                    }

                    if (features.size < recordCount) {
                        hasMore = false
                    } else {
                        offset += recordCount
                    }
                }
            }

            if (allEntities.isNotEmpty()) {
                poiRepository.clearAll()
                poiRepository.insertAll(allEntities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Otrzymano pustą listę punktów turystycznych (POI) od API ArcGis."))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
