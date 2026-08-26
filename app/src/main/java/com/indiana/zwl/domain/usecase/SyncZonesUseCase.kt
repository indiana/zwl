package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.GeoJsonToWkt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import javax.inject.Inject

class SyncZonesUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val zoneRepository: ZoneRepository
) {
    suspend operator fun invoke(): Result<List<Zone>> = withContext(Dispatchers.IO) {
        try {
            val responseStr = arcgisApi.getZanocujWLesieZones()
            val collection = Json.decodeFromString<GeoJsonCollection>(responseStr)
            val zones = mutableListOf<Zone>()

            for (feature in collection.features) {
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
                    val forestDistrict = GeoJsonToWkt.extractForestDistrict(propsMap)
                    val websiteUrl = GeoJsonToWkt.extractWebsiteUrl(propsMap)
                    zones.add(
                        Zone(
                            id = 0,
                            forestDistrict = forestDistrict,
                            geometryWkt = wkt,
                            websiteUrl = websiteUrl
                        )
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

            if (zones.isNotEmpty()) {
                zoneRepository.clearAll()
                zoneRepository.insertAll(zones)
                Result.success(zones)
            } else {
                Result.failure(Exception("Otrzymano pusta liste stref od API ArcGis."))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
