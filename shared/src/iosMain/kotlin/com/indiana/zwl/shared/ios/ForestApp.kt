package com.indiana.zwl.shared.ios

import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.ForestBanSyncParser
import com.indiana.zwl.shared.data.remote.PoiSyncParser
import com.indiana.zwl.shared.data.remote.ZoneSyncParser
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import com.indiana.zwl.shared.map.MapGeoJson
import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.MbtilesTilePackager
import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.data.offline.KtorIosTileFetcher
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * SKIE-friendly facade for the iOS app (SwiftUI). All heavy/suspending work is
 * run on background dispatchers; the Swift side calls these via Task/async.
 */
class ForestApp(
    private val zoneRepository: ZoneRepository,
    private val poiRepository: PoiRepository,
    private val forestBanRepository: ForestBanRepository,
    private val arcgisApi: BdlArcgisApi,
    private val fireApi: BdlFireApi,
    private val offlineStore: MbtilesStore,
    private val httpClient: HttpClient
) {

    private val zoneEngine = SpatialEngine()
    private val banEngine = SpatialEngine()

    @Volatile
    private var cachedZones: List<Zone> = emptyList()

    @Volatile
    private var cachedPois: List<Poi> = emptyList()

    @Volatile
    private var cachedBans: List<ForestBan> = emptyList()

    suspend fun initialize(): Boolean {
        var ok = true
        try {
            if (zoneRepository.getZonesCount() == 0) {
                ok = syncZones() && ok
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: zone sync failed: ${e.message}")
            ok = false
        }

        try {
            cachedBans = forestBanRepository.getAllBans()
            if (cachedBans.isEmpty()) {
                ok = syncBans() && ok
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: ban sync failed: ${e.message}")
            ok = false
        }

        try {
            cachedPois = poiRepository.getAllPois().first()
            if (cachedPois.isEmpty()) {
                ok = syncPois() && ok
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: pois sync failed: ${e.message}")
            ok = false
        }

        if (ok) {
            refreshSpatialIndexes()
        }
        return ok
    }

    suspend fun refreshSpatialIndexes() = withContext(Dispatchers.Default) {
        val zones = zoneRepository.getAllZones()
        val bans = forestBanRepository.getAllBans()
        cachedZones = zones
        cachedBans = bans
        cachedPois = poiRepository.getAllPois().first()
        zoneEngine.initialize(zones)
        banEngine.initializeBans(bans)
    }

    suspend fun syncZones(): Boolean = withContext(Dispatchers.IO) {
        try {
            val responseStr = arcgisApi.getZanocujWLesieZones()
            val collection = Json.decodeFromString<GeoJsonCollection>(responseStr)
            val zones = ZoneSyncParser.parse(collection)
            if (zones.isEmpty()) return@withContext false
            zoneRepository.clearAll()
            zoneRepository.insertAll(zones)
            cachedZones = zones
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp.syncZones failed: ${e.message}")
            false
        }
    }

    suspend fun syncBans(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allBans = mutableListOf<ForestBan>()
            var offset = 0
            val batchSize = 500
            while (true) {
                val collection = arcgisApi.getForestBans(
                    resultOffset = offset,
                    resultRecordCount = batchSize
                )
                val features = collection.features
                if (features.isEmpty()) break
                allBans.addAll(ForestBanSyncParser.parse(collection))
                if (features.size < batchSize) break
                offset += batchSize
            }
            if (allBans.isEmpty()) return@withContext false
            forestBanRepository.clearAll()
            forestBanRepository.insertAll(allBans)
            cachedBans = allBans
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp.syncBans failed: ${e.message}")
            false
        }
    }

    suspend fun syncPois(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allPois = mutableListOf<Poi>()
            for (layerId in listOf(1, 2, 3, 4)) {
                var offset = 0
                val recordCount = 2000
                while (true) {
                    val response = arcgisApi.getTouristPoints(
                        layerId = layerId,
                        outFields = if (layerId == 4) "tur_edu_pnt_cd,tur_obj_desc,nzw_ob" else "tur_rec_pnt_cd,tur_obj_desc,nzw_ob",
                        resultOffset = offset,
                        resultRecordCount = recordCount
                    )
                    val features = response.features
                    if (features.isEmpty()) break
                    allPois.addAll(PoiSyncParser.parseFeatures(features))
                    if (features.size < recordCount) break
                    offset += recordCount
                }
            }
            if (allPois.isEmpty()) return@withContext false
            poiRepository.clearAll()
            poiRepository.insertAll(allPois)
            cachedPois = allPois
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp.syncPois failed: ${e.message}")
            false
        }
    }

    fun checkLocation(latitude: Double, longitude: Double): LocationStatus {
        return zoneEngine.checkLocation(latitude, longitude)
    }

    fun checkForestBan(latitude: Double, longitude: Double): ForestBan? {
        return banEngine.checkForestBan(latitude, longitude)
    }

    suspend fun getFireRisk(latitude: Double, longitude: Double): Int {
        return try {
            val response = fireApi.getFireHazard(geometry = "$longitude,$latitude")
            response.features?.firstOrNull()?.properties?.kodInt ?: -1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            -1
        }
    }

    fun zonesGeoJson(): String = MapGeoJson.zonesToGeoJson(cachedZones)

    fun bansGeoJson(): String = MapGeoJson.bansToGeoJson(cachedBans)

    fun poisGeoJson(): String = MapGeoJson.poisToGeoJson(cachedPois)

    fun cachedZones(): List<Zone> = cachedZones

    fun cachedPois(): List<Poi> = cachedPois

    fun cachedBans(): List<ForestBan> = cachedBans

    suspend fun downloadArea(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double,
        minZoom: Int,
        maxZoom: Int,
        maxTiles: Int,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val packager = MbtilesTilePackager(
            fetcher = KtorIosTileFetcher(httpClient),
            store = offlineStore
        )
        packager.download(
            region = Region(latSouth, latNorth, lonWest, lonEast),
            minZoom = minZoom,
            maxZoom = maxZoom,
            maxTiles = maxTiles,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}

object ForestAppFactory {
    fun create(): ForestApp {
        val koin = koin()
        return ForestApp(
            zoneRepository = koin.get(),
            poiRepository = koin.get(),
            forestBanRepository = koin.get(),
            arcgisApi = koin.get(),
            fireApi = koin.get(),
            offlineStore = koin.get(),
            httpClient = koin.get()
        )
    }
}