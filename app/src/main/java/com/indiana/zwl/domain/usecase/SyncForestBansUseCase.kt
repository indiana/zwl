package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.GeoJsonToWkt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import javax.inject.Inject

class SyncForestBansUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val forestBanRepository: ForestBanRepository
) {
    suspend operator fun invoke(): Result<List<ForestBan>> = withContext(Dispatchers.IO) {
        try {
            val responseStr = arcgisApi.getForestBans()
            val collection = Json.decodeFromString<GeoJsonCollection>(responseStr)
            val bans = mutableListOf<ForestBan>()

            for (feature in collection.features) {
                val properties = feature.properties ?: continue
                val propsMap = mutableMapOf<String, String>()
                for ((key, value) in properties) {
                    propsMap[key] = value?.jsonPrimitive?.content ?: continue
                }
                val wkt = GeoJsonToWkt.geometryToWkt(feature.geometry) ?: continue

                bans.add(
                    ForestBan(
                        id = 0,
                        remoteId = propsMap["objectid"]?.toLongOrNull() ?: 0L,
                        forestDistrictCode = propsMap["kod_nadl"],
                        forestDistrictName = propsMap["nazwa_nadl"] ?: "Nadleśnictwo (Nieznane)",
                        rdlpName = propsMap["nazwa_rdlp"],
                        forestryName = propsMap["lesnictwo"],
                        forestryCode = propsMap["kod_lesn"]?.toIntOrNull(),
                        reason = propsMap["kod"] ?: "Zakaz wstępu do lasu",
                        description = propsMap["opis"],
                        startDate = propsMap["data"],
                        endDate = propsMap["data_koncowa"],
                        forestAddress = propsMap["adr_lesny"] ?: propsMap["adr_silp"],
                        compartmentCode = propsMap["kod_oddzialu"],
                        areaSqMeters = propsMap["st_area(shape)"]?.toDoubleOrNull(),
                        geometryWkt = wkt
                    )
                )
            }

            forestBanRepository.clearAll()
            if (bans.isNotEmpty()) {
                forestBanRepository.insertAll(bans)
            }
            Result.success(bans)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
