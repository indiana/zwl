package com.indiana.zwl.shared.ios

import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.NewSavedPoint
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.domain.util.BdlInfo
import com.indiana.zwl.domain.util.NadlesnictwoUrls
import com.indiana.zwl.shared.data.offline.KtorIosTileFetcher
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.ForestBanSyncParser
import com.indiana.zwl.shared.data.remote.PoiSyncParser
import com.indiana.zwl.shared.data.remote.ZoneSyncParser
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import com.indiana.zwl.shared.map.MapGeoJson
import com.indiana.zwl.shared.offline.MbtilesStoreFactory
import com.indiana.zwl.shared.offline.OfflineAreaDownloadCoordinator
import com.indiana.zwl.shared.offline.OfflineAreaFiles
import com.indiana.zwl.shared.offline.OfflineAreaJanitor
import com.indiana.zwl.shared.offline.OfflineAreaNames
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.offline.TileMath
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.systemTimeZone

/**
 * SKIE-friendly facade for the iOS app (SwiftUI). All heavy/suspending work is
 * run on background dispatchers; the Swift side calls these via Task/async.
 */
class ForestApp(
    private val zoneRepository: ZoneRepository,
    private val poiRepository: PoiRepository,
    private val forestBanRepository: ForestBanRepository,
    private val savedPointRepository: SavedPointRepository,
    private val offlineAreaRepository: OfflineAreaRepository,
    private val offlineStoreFactory: MbtilesStoreFactory,
    private val offlineAreaFiles: OfflineAreaFiles,
    private val arcgisApi: BdlArcgisApi,
    private val fireApi: BdlFireApi,
    private val httpClient: HttpClient,
    private val forestStandUseCase: GetForestStandUseCase
) {

    private val zoneEngine = SpatialEngine()
    private val banEngine = SpatialEngine()

    private val offlineCoordinator = OfflineAreaDownloadCoordinator(
        repository = offlineAreaRepository,
        storeFactory = offlineStoreFactory,
        fetcherProvider = { KtorIosTileFetcher(httpClient) },
        files = offlineAreaFiles,
        nameFormatter = { now ->
            OfflineAreaNames.autoName(now, timeZoneOffsetMinutes(now))
        }
    )

    private var cachedZones: List<Zone> = emptyList()

    private var cachedPois: List<Poi> = emptyList()

    private var cachedBans: List<ForestBan> = emptyList()

    companion object {
        private const val FOREST_STAND_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        // One-shot housekeeping: drop the legacy map.mbtiles + orphaned
        // area files from killed downloads. Best-effort, must not block init.
        try {
            OfflineAreaJanitor(offlineAreaRepository, offlineAreaFiles).run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: offline area janitor failed: ${e.message}")
        }

        var ok = true
        try {
            if (zoneRepository.getZonesCount() == 0) {
                ok = syncZones() && ok
            } else {
                val zones = zoneRepository.getAllZones()
                if (zones.any { it.forestDistrict.contains("Nieznane", ignoreCase = true) }) {
                    ok = syncZones() && ok
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: zone sync failed: ${e.message}")
            ok = false
        }

        // Android refreshes forest bans and POIs on every launch (they carry
        // validity dates / can change); only zones are synced lazily above.
        // Mirror that — keep the cached copy when the refresh fails.
        try {
            cachedBans = forestBanRepository.getAllBans()
            if (!syncBans()) ok = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: ban sync failed: ${e.message}")
            ok = false
        }

        try {
            cachedPois = poiRepository.getAllPois().first()
            if (!syncPois()) ok = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp: pois sync failed: ${e.message}")
            ok = false
        }

        refreshSpatialIndexes()
        ok
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

    suspend fun syncZones(): Boolean = withContext(Dispatchers.Default) {
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

    suspend fun syncBans(): Boolean = withContext(Dispatchers.Default) {
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

    suspend fun syncPois(): Boolean = withContext(Dispatchers.Default) {
        try {
            val allPois = mutableListOf<Poi>()
            for (layerId in listOf(0, 1, 2, 3, 4)) {
                var offset = 0
                val recordCount = 2000
                while (true) {
                    val response = arcgisApi.getTouristPoints(
                        layerId = layerId,
                        outFields = when (layerId) {
                            0 -> "tur_sleep_pnt_cd,tur_obj_desc,nzw_ob"
                            4 -> "tur_edu_pnt_cd,tur_obj_desc,nzw_ob"
                            else -> "tur_rec_pnt_cd,tur_obj_desc,nzw_ob"
                        },
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

    suspend fun checkLocation(latitude: Double, longitude: Double): LocationStatus =
        withContext(Dispatchers.Default) {
            zoneEngine.checkLocation(latitude, longitude)
        }

    suspend fun checkForestBan(latitude: Double, longitude: Double): ForestBan? =
        withContext(Dispatchers.Default) {
            banEngine.checkForestBan(latitude, longitude)
        }

    suspend fun getFireRisk(latitude: Double, longitude: Double): Int = withContext(Dispatchers.Default) {
        try {
            val response = fireApi.getFireHazard(geometry = "$longitude,$latitude")
            response.features?.firstOrNull()?.properties?.kodInt ?: -1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            -1
        }
    }

    suspend fun zonesGeoJson(): String =
        withContext(Dispatchers.Default) { MapGeoJson.zonesToGeoJson(cachedZones) }

    suspend fun bansGeoJson(): String =
        withContext(Dispatchers.Default) { MapGeoJson.bansToGeoJson(cachedBans) }

    suspend fun poisGeoJson(): String =
        withContext(Dispatchers.Default) { MapGeoJson.poisToGeoJson(cachedPois) }

    suspend fun savedPoints(): List<SavedPoint> =
        withContext(Dispatchers.Default) { savedPointRepository.getAllPoints().first() }

    suspend fun savePoint(name: String, latitude: Double, longitude: Double): Long =
        withContext(Dispatchers.Default) {
            savedPointRepository.insert(NewSavedPoint(name, latitude, longitude))
        }

    suspend fun renameSavedPoint(id: Long, name: String) = withContext(Dispatchers.Default) {
        savedPointRepository.rename(id, name)
    }

    suspend fun deleteSavedPoint(id: Long) = withContext(Dispatchers.Default) {
        savedPointRepository.delete(id)
    }

    suspend fun savedPointsGeoJson(): String =
        withContext(Dispatchers.Default) {
            MapGeoJson.savedPointsToGeoJson(savedPointRepository.getAllPoints().first())
        }

    fun cachedZones(): List<Zone> = cachedZones

    fun cachedPois(): List<Poi> = cachedPois

    fun cachedBans(): List<ForestBan> = cachedBans

    suspend fun getForestStand(zone: Zone): ForestStandSummary? = withContext(Dispatchers.Default) {
        try {
            forestStandUseCase(zone).getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp.getForestStand failed: ${e.message}")
            null
        }
    }

    fun cachedForestStand(zone: Zone): ForestStandSummary? {
        val json = zone.forestStandJson ?: return null
        return try {
            Json.decodeFromString<ForestStandSummary>(json)
        } catch (e: Exception) {
            println("ForestApp.cachedForestStand decode failed: ${e.message}")
            null
        }
    }

    suspend fun cacheForestStand(zone: Zone, summary: ForestStandSummary, timestamp: Long) {
        try {
            val json = Json.encodeToString(summary)
            zoneRepository.updateForestStand(zone.forestDistrict, json, timestamp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ForestApp.cacheForestStand failed: ${e.message}")
        }
    }

    fun isForestStandCacheStale(zone: Zone, now: Long): Boolean {
        val timestamp = zone.forestStandTimestamp ?: return true
        return now - timestamp > FOREST_STAND_CACHE_MAX_AGE_MS
    }

    fun forestStandCacheMaxAgeMs(): Long = FOREST_STAND_CACHE_MAX_AGE_MS

    fun speciesWikipediaTitle(code: String): String? = BdlInfo.wikipediaTitleForSpecies(code)

    fun wikipediaPageUrl(title: String): String = "${BdlInfo.WIKIPEDIA_BASE_URL}$title"

    fun forestFunTooltip(code: String): String? = BdlInfo.tooltipForForestFun(code)

    fun standStruTooltip(code: String): String? = BdlInfo.tooltipForStandStru(code)

    fun siteTypeTooltip(code: String): String? = BdlInfo.tooltipForSiteType(code)

    fun protCategTooltip(code: String): String? = BdlInfo.tooltipForProtCateg(code)

    fun forestStandRotationAgeText(summary: ForestStandSummary): String? {
        val age = summary.rotationAge ?: return null
        return "$age lat"
    }

    fun rotationAgeTooltip(): String = BdlInfo.rotationAgeTooltip

    fun nadlesnictwoWebsiteUrl(districtName: String?, rdlpName: String?): String? =
        NadlesnictwoUrls.websiteUrl(districtName, rdlpName)

    fun nadlesnictwoWebsiteHost(url: String?): String? = NadlesnictwoUrls.displayHost(url)

    suspend fun offlineAreas(): List<DownloadedArea> = withContext(Dispatchers.Default) {
        offlineAreaRepository.getAll()
    }

    suspend fun deleteOfflineArea(id: Long) = withContext(Dispatchers.Default) {
        val area = offlineAreaRepository.getAll().find { it.id == id } ?: return@withContext
        offlineAreaRepository.delete(id)
        offlineAreaFiles.deleteFile(area.fileName)
    }

    suspend fun renameOfflineArea(id: Long, name: String) = withContext(Dispatchers.Default) {
        offlineAreaRepository.rename(id, name)
    }

    suspend fun deleteAllOfflineAreas() = withContext(Dispatchers.Default) {
        val areas = offlineAreaRepository.getAll()
        offlineAreaRepository.deleteAll()
        areas.forEach { offlineAreaFiles.deleteFile(it.fileName) }
    }

    suspend fun refreshOfflineArea(
        id: Long,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        val area = offlineAreaRepository.getAll().find { it.id == id }
        if (area == null) {
            onError("Nie znaleziono obszaru do odświeżenia.")
            return@withContext
        }
        offlineCoordinator.refresh(
            area = area,
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /** Absolute path of an area file — feeds MapLibre's `mbtiles://` source. */
    fun offlineAreaFilePath(fileName: String): String = offlineAreaFiles.filePath(fileName)

    /** Tile count the view spans — lets Swift reject oversized areas with a
     *  clear alert before any download starts (Android parity). */
    suspend fun estimateAreaTiles(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double
    ): Int = withContext(Dispatchers.Default) {
        TileMath.estimateTileCount(
            Region(latSouth, latNorth, lonWest, lonEast),
            OfflineLimits.MIN_ZOOM,
            OfflineLimits.MAX_ZOOM
        )
    }

    suspend fun downloadArea(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double,
        onProgress: (Float, String) -> Unit,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        offlineCoordinator.download(
            region = Region(latSouth, latNorth, lonWest, lonEast),
            onProgress = onProgress,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun timeZoneOffsetMinutes(atMillis: Long): Int =
        (NSTimeZone.systemTimeZone.secondsFromGMT) / 60
}