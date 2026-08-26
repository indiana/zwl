package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.GeoJsonToWkt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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

                for (feature in features) {
                    try {
                        val properties = feature.properties ?: continue
                        val propsMap = mutableMapOf<String, String>()
                        for ((key, value) in properties) {
                            val strValue = value?.jsonPrimitive?.contentOrNull
                            if (!strValue.isNullOrBlank()) {
                                propsMap[key] = strValue
                            }
                        }
                        val wkt = GeoJsonToWkt.geometryToWkt(feature.geometry) ?: continue

                        allBans.add(
                            ForestBan(
                                id = 0,
                                remoteId = propsMap["objectid"]?.toLongOrNull() ?: 0L,
                                forestDistrictCode = propsMap["kod_nadl"],
                                forestDistrictName = propsMap["nazwa_nadl"] ?: "Nadleśnictwo (Nieznane)",
                                rdlpName = propsMap["nazwa_rdlp"],
                                forestryName = propsMap["lesnictwo"],
                                forestryCode = propsMap["kod_lesn"]?.toIntOrNull(),
                                reason = propsMap["kod"] ?: "Zakaz wstepu do lasu",
                                description = propsMap["opis"],
                                startDate = propsMap["data"],
                                endDate = propsMap["data_koncowa"],
                                forestAddress = propsMap["adr_lesny"] ?: propsMap["adr_silp"],
                                compartmentCode = propsMap["kod_oddzialu"],
                                areaSqMeters = propsMap["st_area(shape)"]?.toDoubleOrNull(),
                                geometryWkt = wkt
                            )
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                    }
                }

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
