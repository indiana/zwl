package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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

                    for (feature in features) {
                        val properties = feature.properties
                        val geom = feature.geometry

                        if (geom.type.equals("point", ignoreCase = true) && geom.coordinates is JsonArray) {
                            val coords = geom.coordinates.jsonArray
                            if (coords.size >= 2) {
                                val lon = coords[0].jsonPrimitive.double
                                val lat = coords[1].jsonPrimitive.double

                                val code = properties?.get("tur_rec_pnt_cd")?.jsonPrimitive?.content
                                    ?: properties?.get("tur_edu_pnt_cd")?.jsonPrimitive?.content
                                    ?: ""
                                val desc = properties?.get("tur_obj_desc")?.jsonPrimitive?.content ?: ""
                                val name = properties?.get("nzw_ob")?.jsonPrimitive?.content ?: ""

                                allPois.add(
                                    Poi(
                                        id = 0,
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

            if (allPois.isNotEmpty()) {
                poiRepository.clearAll()
                poiRepository.insertAll(allPois)
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
