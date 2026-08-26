package com.indiana.zwl.presentation.map.util

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.geom.Coordinate
import org.maplibre.android.geometry.LatLng

data class PolygonRings(val outer: List<LatLng>, val holes: List<List<LatLng>>)

data class CachedGeometry(
    val rings: List<PolygonRings>,
    val jtsPolygons: List<Polygon>
)

data class IndexedEntry(val id: Long, val polygon: Polygon)

class GeometryCache {
    private val parsedCache = mutableMapOf<String, CachedGeometry>()
    private var zoneIndex = STRtree()
    private var banIndex = STRtree()
    private val wktReader = WKTReader()
    private val gf = GeometryFactory()

    private val zoneData = mutableMapOf<Long, CachedGeometry>()
    private val banData = mutableMapOf<Long, CachedGeometry>()

    fun parse(wkt: String): CachedGeometry? {
        parsedCache[wkt]?.let { return it }

        return try {
            var geom = wktReader.read(wkt) ?: return null
            if (!geom.isValid()) {
                try { geom = geom.buffer(0.0) } catch (_: Throwable) {}
            }

            val rings = mutableListOf<PolygonRings>()
            val jtsPolys = mutableListOf<Polygon>()
            val numGeoms = geom.getNumGeometries()
            for (g in 0 until numGeoms) {
                val subGeom = geom.getGeometryN(g)
                if (subGeom is Polygon) {
                    jtsPolys.add(subGeom)
                    val outer = subGeom.getExteriorRing().getCoordinates()
                        .map { c -> LatLng(c.getY(), c.getX()) }
                    val holes = mutableListOf<List<LatLng>>()
                    for (h in 0 until subGeom.getNumInteriorRing()) {
                        holes.add(subGeom.getInteriorRingN(h).getCoordinates()
                            .map { c -> LatLng(c.getY(), c.getX()) })
                    }
                    rings.add(PolygonRings(outer, holes))
                }
            }

            val cached = CachedGeometry(rings, jtsPolys)
            parsedCache[wkt] = cached
            cached
        } catch (_: Throwable) {
            null
        }
    }

    fun addZonePolygon(id: Long, wkt: String) {
        val cached = parse(wkt) ?: return
        zoneData[id] = cached
        for (poly in cached.jtsPolygons) {
            zoneIndex.insert(poly.getEnvelopeInternal(), IndexedEntry(id, poly))
        }
    }

    fun addBanPolygon(id: Long, wkt: String) {
        val cached = parse(wkt) ?: return
        banData[id] = cached
        for (poly in cached.jtsPolygons) {
            banIndex.insert(poly.getEnvelopeInternal(), IndexedEntry(id, poly))
        }
    }

    fun buildIndices() {
        zoneIndex.build()
        banIndex.build()
    }

    fun clearZones() {
        zoneIndex = STRtree()
        zoneData.clear()
    }

    fun clearBans() {
        banIndex = STRtree()
        banData.clear()
    }

    fun createPoint(longitude: Double, latitude: Double): Point {
        return gf.createPoint(Coordinate(longitude, latitude))
    }

    fun findZoneIdAt(point: Point): Long? {
        val candidates = zoneIndex.query(point.getEnvelopeInternal()) as List<IndexedEntry>
        for (entry in candidates) {
            val safePoly = if (!entry.polygon.isValid()) {
                try { entry.polygon.buffer(0.0) as? Polygon ?: entry.polygon } catch (_: Throwable) { entry.polygon }
            } else entry.polygon
            if (safePoly.contains(point)) return entry.id
        }
        return null
    }

    fun findBanIdAt(point: Point): Long? {
        val candidates = banIndex.query(point.getEnvelopeInternal()) as List<IndexedEntry>
        for (entry in candidates) {
            val safePoly = if (!entry.polygon.isValid()) {
                try { entry.polygon.buffer(0.0) as? Polygon ?: entry.polygon } catch (_: Throwable) { entry.polygon }
            } else entry.polygon
            if (safePoly.contains(point)) return entry.id
        }
        return null
    }

    fun queryZoneIdsInEnvelope(envelope: Envelope): Set<Long> {
        val candidates = zoneIndex.query(envelope) as List<IndexedEntry>
        return candidates.map { it.id }.toSet()
    }

    fun queryBanIdsInEnvelope(envelope: Envelope): Set<Long> {
        val candidates = banIndex.query(envelope) as List<IndexedEntry>
        return candidates.map { it.id }.toSet()
    }

    fun getZoneRings(id: Long): List<PolygonRings>? = zoneData[id]?.rings
    fun getBanRings(id: Long): List<PolygonRings>? = banData[id]?.rings
}
