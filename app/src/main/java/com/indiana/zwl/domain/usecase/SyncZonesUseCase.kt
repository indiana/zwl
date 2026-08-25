package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.util.GeoJsonConverter
import org.locationtech.jts.io.WKTWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncZonesUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val zoneRepository: ZoneRepository
) {
    suspend operator fun invoke(): Result<List<Zone>> = withContext(Dispatchers.IO) {
        try {
            val responseBody = arcgisApi.getZanocujWLesieZones()
            val wktWriter = WKTWriter()
            val entities = mutableListOf<ZoneEntity>()

            responseBody.use { body ->
                GeoJsonConverter.parseFeatureCollectionStream(body.charStream()) { properties, geometry ->
                    val wkt = wktWriter.write(geometry)
                    val forestDistrict = GeoJsonConverter.extractForestDistrict(properties)
                    val websiteUrl = GeoJsonConverter.extractWebsiteUrl(properties)
                    entities.add(
                        ZoneEntity(
                            forestDistrict = forestDistrict,
                            geometryWkt = wkt,
                            websiteUrl = websiteUrl
                        )
                    )
                }
            }

            if (entities.isNotEmpty()) {
                zoneRepository.clearAll()
                zoneRepository.insertAll(entities.map { it.toDomainModel() })
                Result.success(entities.map { it.toDomainModel() })
            } else {
                Result.failure(Exception("Otrzymano pustą listę stref od API ArcGis."))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
