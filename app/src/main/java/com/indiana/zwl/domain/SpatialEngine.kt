package com.indiana.zwl.domain

import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.domain.model.Zone
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.distance.DistanceOp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SpatialEngine {

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
    
    @Volatile
    private var engineState: EngineState? = null

    data class ParsedZone(
        val forestDistrict: String,
        val geometry: Geometry
    )
    
    class EngineState(
        val strTree: STRtree,
        val parsedZones: List<ParsedZone>
    )

    fun initialize(zones: List<Zone>) {
        val wktReader = WKTReader(geometryFactory)
        val newParsedZones = ArrayList<ParsedZone>()
        val newStrTree = STRtree()

        for (zone in zones) {
            try {
                val geom = wktReader.read(zone.geometryWkt)
                val parsed = ParsedZone(zone.forestDistrict, geom)
                newParsedZones.add(parsed)
                newStrTree.insert(geom.envelopeInternal, parsed)
            } catch (e: Exception) {
                // Ignoruj błędne geometrie
            }
        }
        newStrTree.build()
        engineState = EngineState(newStrTree, newParsedZones)
    }

    fun checkLocation(latitude: Double, longitude: Double): LocationStatus {
        val state = engineState ?: return LocationStatus.EmptyData
        if (state.parsedZones.isEmpty()) {
            return LocationStatus.EmptyData
        }

        val userCoord = Coordinate(longitude, latitude)
        val userPoint = geometryFactory.createPoint(userCoord)

        // 1. Zgrubne wyszukiwanie kandydatów (Bounding Box o zerowej wielkości - nakładanie)
        val searchEnvelope = Envelope(userCoord)
        @Suppress("UNCHECKED_CAST")
        val exactCandidates = state.strTree.query(searchEnvelope) as List<ParsedZone>

        // Dokładny test Point-in-Polygon
        for (candidate in exactCandidates) {
            if (candidate.geometry.contains(userPoint)) {
                return LocationStatus.InZone(candidate.forestDistrict)
            }
        }

        // 2. Jeśli poza strefą - znajdź najbliższą strefę.
        // Najpierw szukamy w promieniu ok. 11 km (0.1 stopnia), aby uniknąć sprawdzania całego kraju.
        val searchEnv = Envelope(userCoord)
        searchEnv.expandBy(0.1)
        @Suppress("UNCHECKED_CAST")
        val distanceCandidates = state.strTree.query(searchEnv) as List<ParsedZone>

        var nearestZone: ParsedZone? = null
        var minDistanceMeters = Double.MAX_VALUE
        var targetCoord: Coordinate? = null

        if (distanceCandidates.isEmpty()) {
            // Jesteśmy daleko od jakiejkolwiek strefy (ponad 11 km).
            // Obliczanie dokładnego dystansu do skomplikowanych poligonów jest bardzo kosztowne i może zablokować wątek.
            // Zamiast tego, bierzemy po prostu strefę z najbliższym Bounding Boxem.
            nearestZone = state.parsedZones.minByOrNull {
                it.geometry.envelopeInternal.distance(searchEnvelope)
            }
            
            // Używamy środka Bounding Boxa strefy jako punktu docelowego, by uniknąć kosztownego DistanceOp.
            nearestZone?.let { zone ->
                val env = zone.geometry.envelopeInternal
                targetCoord = Coordinate(env.centre().x, env.centre().y)
                minDistanceMeters = calculateHaversineDistance(
                    latitude, longitude,
                    targetCoord!!.y, targetCoord!!.x
                )
            }
        } else {
            var closestDistanceDeg = Double.MAX_VALUE
            for (zone in distanceCandidates) {
                val distDeg = zone.geometry.distance(userPoint)
                if (distDeg < closestDistanceDeg) {
                    closestDistanceDeg = distDeg
                    nearestZone = zone
                }
            }

            nearestZone?.let { zone ->
                val distanceOp = DistanceOp(zone.geometry, userPoint)
                val nearestCoords = distanceOp.nearestPoints()
                targetCoord = nearestCoords[0]
                minDistanceMeters = calculateHaversineDistance(
                    latitude, longitude,
                    targetCoord!!.y, targetCoord!!.x
                )
            }
        }

        val zone = nearestZone
        val target = targetCoord
        return if (zone != null && target != null) {
            val bearing = calculateInitialBearing(latitude, longitude, target.y, target.x)
            LocationStatus.OutsideZone(
                nearestDistrict = zone.forestDistrict,
                distanceMeters = minDistanceMeters,
                bearingDegrees = bearing
            )
        } else {
            LocationStatus.EmptyData
        }
    }

    private fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateInitialBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad).toFloat()
        return (bearingDeg + 360f) % 360f
    }
}
