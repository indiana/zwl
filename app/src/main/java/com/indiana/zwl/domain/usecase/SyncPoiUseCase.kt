package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.PoiDao
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.data.remote.BdlArcgisApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SyncPoiUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val poiDao: PoiDao
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            val allEntities = mutableListOf<PoiEntity>()
            val layers = listOf(1, 2, 3, 4)

            for (layerId in layers) {
                var offset = 0
                var hasMore = true
                val recordCount = 2000

                while (hasMore) {
                    val response = arcgisApi.getTouristPoints(
                        layerId = layerId,
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
                                
                                val code = properties?.get("tur_rec_pnt_cd") as? String ?: ""
                                val desc = properties?.get("tur_obj_desc") as? String ?: ""
                                val name = properties?.get("nzw_ob") as? String ?: ""

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
                poiDao.clearAll()
                poiDao.insertAll(allEntities)
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
