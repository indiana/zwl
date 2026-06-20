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

import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.indiana.zwl.data.local.ZoneDao

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val zoneDao: ZoneDao,
    private val arcgisApi: BdlArcgisApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
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
