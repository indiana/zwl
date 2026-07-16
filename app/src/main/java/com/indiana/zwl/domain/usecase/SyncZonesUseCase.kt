package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.util.GeoJsonConverter
import org.locationtech.jts.io.WKTWriter
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SyncZonesUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val zoneDao: ZoneDao
) {
    suspend operator fun invoke(): Result<List<Zone>> {
        return try {
            val responseBody = arcgisApi.getZanocujWLesieZones()
            val wktWriter = WKTWriter()
            val entities = mutableListOf<ZoneEntity>()

            responseBody.use { body ->
                GeoJsonConverter.parseFeatureCollectionStream(body.charStream()) { properties, geometry ->
                    val wkt = wktWriter.write(geometry)
                    val forestDistrict = GeoJsonConverter.extractForestDistrict(properties)
                    entities.add(
                        ZoneEntity(
                            forestDistrict = forestDistrict,
                            geometryWkt = wkt
                        )
                    )
                }
            }

            if (entities.isNotEmpty()) {
                zoneDao.clearAll()
                zoneDao.insertAll(entities)
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
