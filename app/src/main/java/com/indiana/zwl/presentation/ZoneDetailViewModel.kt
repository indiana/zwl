package com.indiana.zwl.presentation

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.operation.distance.DistanceOp
import javax.inject.Inject

@HiltViewModel
class ZoneDetailViewModel @Inject constructor(
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getForestStandUseCase: GetForestStandUseCase,
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    private val _selectedZoneDetails = kotlinx.coroutines.flow.MutableStateFlow<SelectedZoneDetails?>(null)
    val selectedZoneDetails: kotlinx.coroutines.flow.StateFlow<SelectedZoneDetails?> = _selectedZoneDetails

    private val _selectedPoiDetails = kotlinx.coroutines.flow.MutableStateFlow<SelectedPoiDetails?>(null)
    val selectedPoiDetails: kotlinx.coroutines.flow.StateFlow<SelectedPoiDetails?> = _selectedPoiDetails

    fun clearSelectedZone() {
        _selectedZoneDetails.value = null
    }

    fun clearSelectedPoi() {
        _selectedPoiDetails.value = null
    }

    fun updateDistanceFromUser(lat: Double, lon: Double) {
        _selectedPoiDetails.value?.let { currentPoiDetails ->
            val results = FloatArray(1)
            Location.distanceBetween(
                lat, lon,
                currentPoiDetails.poi.latitude, currentPoiDetails.poi.longitude,
                results
            )
            _selectedPoiDetails.value = currentPoiDetails.copy(distanceMeters = results[0].toDouble())
        }
    }

    fun selectPoi(poi: PoiEntity, userLat: Double?, userLon: Double?) {
        viewModelScope.launch {
            _selectedZoneDetails.value = null
            val distance = if (userLat != null && userLon != null) {
                val results = FloatArray(1)
                Location.distanceBetween(userLat, userLon, poi.latitude, poi.longitude, results)
                results[0].toDouble()
            } else null

            _selectedPoiDetails.value = SelectedPoiDetails(poi = poi, distanceMeters = distance)
        }
    }

    fun selectZone(zone: Zone, jtsPolygon: org.locationtech.jts.geom.Geometry, clickLat: Double, clickLon: Double, userLat: Double?, userLon: Double?) {
        _selectedZoneDetails.value = initialZoneDetails(zone)
        viewModelScope.launch {
            try {
                _selectedPoiDetails.value = null

                val distance = if (userLat != null && userLon != null) {
                    withContext(Dispatchers.Default) {
                        try {
                            val gf = GeometryFactory()
                            val userPoint = gf.createPoint(Coordinate(userLon, userLat))
                            val targetGeom = if (!jtsPolygon.isValid) {
                                try { jtsPolygon.buffer(0.0) } catch (_: Throwable) { jtsPolygon }
                            } else jtsPolygon
                            val distanceOp = DistanceOp(targetGeom, userPoint)
                            val nearestCoords = distanceOp.nearestPoints()
                            val targetCoord = nearestCoords[0]
                            val results = FloatArray(1)
                            Location.distanceBetween(userLat, userLon, targetCoord.y, targetCoord.x, results)
                            results[0].toDouble()
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            e.printStackTrace()
                            null
                        }
                    }
                } else null

                val cachedForestStand = loadCachedForestStand(zone)
                val needsForestStandRefresh = cachedForestStand == null || isForestStandCacheStale(zone)

                if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                    _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                        distanceMeters = distance,
                        forestStand = cachedForestStand,
                        isLoadingForestStand = needsForestStandRefresh
                    )
                }

                val tempLoc = Location("").apply {
                    latitude = clickLat
                    longitude = clickLon
                }

                val fireRiskResult = getFireRiskUseCase(tempLoc)
                val riskCode = if (fireRiskResult.isSuccess) {
                    val code = fireRiskResult.getOrDefault(-1)
                    if (code in 0..3) {
                        val timestamp = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            zoneRepository.updateFireRisk(zone.forestDistrict, code, timestamp)
                        }
                    }
                    code
                } else {
                    val exception = fireRiskResult.exceptionOrNull()
                    if (isNetworkException(exception)) {
                        val freshZone = withContext(Dispatchers.IO) { zoneRepository.getByForestDistrict(zone.forestDistrict) }
                        resolveCachedFireRisk(freshZone?.fireRiskLevel, freshZone?.fireRiskTimestamp)
                    } else {
                        -1
                    }
                }

                if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                    _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                        fireRiskLevel = riskCode,
                        isLoadingFireRisk = false
                    )
                }

                if (needsForestStandRefresh) {
                    val forestStandResult = getForestStandUseCase(zone)
                    if (_selectedZoneDetails.value?.zone?.id == zone.id) {
                        if (forestStandResult.isSuccess) {
                            val summary = forestStandResult.getOrNull()
                            if (summary != null) {
                                val json = Gson().toJson(summary)
                                val timestamp = System.currentTimeMillis()
                                withContext(Dispatchers.IO) {
                                    zoneRepository.updateForestStand(zone.forestDistrict, json, timestamp)
                                }
                            }
                            _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                                forestStand = summary,
                                isLoadingForestStand = false
                            )
                        } else {
                            _selectedZoneDetails.value = _selectedZoneDetails.value?.copy(
                                isLoadingForestStand = false
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    fun selectZoneByDistrict(districtName: String, zones: List<Zone>, userLat: Double?, userLon: Double?) {
        val zone = zones.firstOrNull { it.forestDistrict.equals(districtName, ignoreCase = true) } ?: return
        _selectedZoneDetails.value = initialZoneDetails(zone)
        viewModelScope.launch {
            try {
                val (jtsPolygon, lat, lon) = withContext(Dispatchers.Default) {
                    val polygon = org.locationtech.jts.io.WKTReader().read(zone.geometryWkt)
                    val centroid = polygon.centroid
                    val useLat = userLat ?: centroid.y
                    val useLon = userLon ?: centroid.x
                    Triple(polygon, useLat, useLon)
                }
                selectZone(zone, jtsPolygon, lat, lon, userLat, userLon)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    private fun initialZoneDetails(zone: Zone) = SelectedZoneDetails(
        zone = zone,
        distanceMeters = null,
        fireRiskLevel = -1,
        isLoadingFireRisk = true,
        forestStand = null,
        isLoadingForestStand = true
    )

    private fun loadCachedForestStand(zone: Zone): ForestStandSummary? {
        val json = zone.forestStandJson ?: return null
        return try {
            Gson().fromJson(json, ForestStandSummary::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isForestStandCacheStale(zone: Zone): Boolean {
        val timestamp = zone.forestStandTimestamp ?: return true
        return System.currentTimeMillis() - timestamp > FOREST_STAND_CACHE_MAX_AGE_MS
    }

    private fun resolveCachedFireRisk(level: Int?, timestamp: Long?): Int {
        val now = System.currentTimeMillis()
        return if (level != null && level in 0..3 &&
            timestamp != null && now - timestamp < FIRE_RISK_CACHE_MAX_AGE_MS
        ) {
            level + 10
        } else {
            -2
        }
    }

    private fun isNetworkException(e: Throwable?): Boolean {
        return e is java.net.UnknownHostException ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException ||
               e is java.net.SocketException ||
               e is javax.net.ssl.SSLException
    }

    companion object {
        private const val FIRE_RISK_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val FOREST_STAND_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
