package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.ZoneSyncParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
            val zones = ZoneSyncParser.parse(collection)

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
