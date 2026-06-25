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
            val response = arcgisApi.getZanocujWLesieZones()
            val wktWriter = WKTWriter()

            val entities = response.features.mapNotNull { feature ->
                val jtsGeom = GeoJsonConverter.toJtsGeometry(feature.geometry)
                if (jtsGeom != null) {
                    val wkt = wktWriter.write(jtsGeom)
                    ZoneEntity(
                        forestDistrict = feature.forestDistrict,
                        geometryWkt = wkt
                    )
                } else {
                    null
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
