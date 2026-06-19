package com.indiana.zwl.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.data.local.ZwlDatabase
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.domain.util.GeoJsonConverter
import org.locationtech.jts.io.WKTWriter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = ZwlDatabase.getDatabase(applicationContext)
        val zoneDao = database.zoneDao()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://wfs.bdl.lasy.gov.pl/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val arcgisApi = retrofit.create(BdlArcgisApi::class.java)

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
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
