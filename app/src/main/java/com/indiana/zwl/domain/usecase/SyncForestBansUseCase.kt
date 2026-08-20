package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ForestBanDao
import com.indiana.zwl.data.local.ForestBanEntity
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.util.GeoJsonConverter
import org.locationtech.jts.io.WKTWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncForestBansUseCase @Inject constructor(
    private val arcgisApi: BdlArcgisApi,
    private val forestBanDao: ForestBanDao
) {
    suspend operator fun invoke(): Result<List<ForestBan>> = withContext(Dispatchers.IO) {
        try {
            val responseBody = arcgisApi.getForestBans()
            val wktWriter = WKTWriter()
            val entities = mutableListOf<ForestBanEntity>()

            responseBody.use { body ->
                GeoJsonConverter.parseFeatureCollectionStream(body.charStream()) { properties, geometry ->
                    val wkt = wktWriter.write(geometry)
                    val remoteId = properties["objectid"]?.toLongOrNull() ?: 0L
                    val forestDistrictCode = properties["kod_nadl"]
                    val forestDistrictName = properties["nazwa_nadl"]
                    val rdlpName = properties["nazwa_rdlp"]
                    val forestryName = properties["lesnictwo"]
                    val forestryCode = properties["kod_lesn"]?.toIntOrNull()
                    val reason = properties["kod"]
                    val description = properties["opis"]
                    val startDate = properties["data"]
                    val endDate = properties["data_koncowa"]
                    val forestAddress = properties["adr_lesny"] ?: properties["adr_silp"]
                    val compartmentCode = properties["kod_oddzialu"]
                    val areaSqMeters = properties["st_area(shape)"]?.toDoubleOrNull()

                    entities.add(
                        ForestBanEntity(
                            remoteId = remoteId,
                            forestDistrictCode = forestDistrictCode,
                            forestDistrictName = forestDistrictName,
                            rdlpName = rdlpName,
                            forestryName = forestryName,
                            forestryCode = forestryCode,
                            reason = reason,
                            description = description,
                            startDate = startDate,
                            endDate = endDate,
                            forestAddress = forestAddress,
                            compartmentCode = compartmentCode,
                            areaSqMeters = areaSqMeters,
                            geometryWkt = wkt
                        )
                    )
                }
            }

            forestBanDao.clearAll()
            if (entities.isNotEmpty()) {
                forestBanDao.insertAll(entities)
            }
            Result.success(entities.map { it.toDomainModel() })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
