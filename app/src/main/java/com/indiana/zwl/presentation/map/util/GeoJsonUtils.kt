package com.indiana.zwl.presentation.map.util

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.geom.Coordinate
import org.maplibre.android.geometry.LatLng
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.domain.util.uiGroup
import java.util.concurrent.ConcurrentHashMap

data class PolygonRings(val outer: List<LatLng>, val holes: List<List<LatLng>>)

data class CachedGeometry(
    val rings: List<PolygonRings>,
    val jtsPolygons: List<Polygon>
)

data class IndexedEntry(val id: Long, val polygon: Polygon)

private val globalParsedCache = ConcurrentHashMap<String, CachedGeometry>()

fun clearParsedGeometryCache() {
    globalParsedCache.clear()
}

private fun parseWktGeometry(wkt: String): CachedGeometry? {
    globalParsedCache[wkt]?.let { return it }

    return try {
        val reader = WKTReader()
        var geom = reader.read(wkt) ?: return null
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
        globalParsedCache[wkt] = cached
        cached
    } catch (_: Throwable) {
        null
    }
}

class GeometryCache {
    private var zoneIndex = STRtree()
    private var banIndex = STRtree()
    private val gf = GeometryFactory()

    private val zoneData = mutableMapOf<Long, CachedGeometry>()
    private val banData = mutableMapOf<Long, CachedGeometry>()

    fun parse(wkt: String): CachedGeometry? = parseWktGeometry(wkt)

    fun addZonePolygon(id: Long, wkt: String) {
        val cached = parse(wkt) ?: return
        insertZone(id, cached)
    }

    fun addBanPolygon(id: Long, wkt: String) {
        val cached = parse(wkt) ?: return
        insertBan(id, cached)
    }

    private fun insertZone(id: Long, cached: CachedGeometry) {
        zoneData[id] = cached
        for (poly in cached.jtsPolygons) {
            zoneIndex.insert(poly.getEnvelopeInternal(), IndexedEntry(id, poly))
        }
    }

    private fun insertBan(id: Long, cached: CachedGeometry) {
        banData[id] = cached
        for (poly in cached.jtsPolygons) {
            banIndex.insert(poly.getEnvelopeInternal(), IndexedEntry(id, poly))
        }
    }

    fun buildZoneIndex() {
        zoneIndex.build()
    }

    fun buildBanIndex() {
        banIndex.build()
    }

    fun adoptZonesFrom(other: GeometryCache) {
        zoneData.clear()
        zoneData.putAll(other.zoneData)
        zoneIndex = other.zoneIndex
    }

    fun adoptBansFrom(other: GeometryCache) {
        banData.clear()
        banData.putAll(other.banData)
        banIndex = other.banIndex
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

    fun getZoneRings(id: Long): List<PolygonRings>? = zoneData[id]?.rings
    fun getBanRings(id: Long): List<PolygonRings>? = banData[id]?.rings
    fun zoneDataAccessor(id: Long): CachedGeometry? = zoneData[id]
    fun banDataAccessor(id: Long): CachedGeometry? = banData[id]
}

private fun coordArray(coords: Array<Coordinate>): String {
    val sb = StringBuilder("[")
    for (i in coords.indices) {
        if (i > 0) sb.append(",")
        sb.append("[").append(coords[i].x).append(",").append(coords[i].y).append("]")
    }
    sb.append("]")
    return sb.toString()
}

private fun polygonToGeoJson(poly: Polygon): String {
    val sb = StringBuilder()
    sb.append("{\"type\":\"Polygon\",\"coordinates\":[")
    sb.append(coordArray(poly.getExteriorRing().getCoordinates()))
    for (h in 0 until poly.getNumInteriorRing()) {
        sb.append(",")
        sb.append(coordArray(poly.getInteriorRingN(h).getCoordinates()))
    }
    sb.append("]}")
    return sb.toString()
}

fun buildZoneGeoJson(geometryCache: GeometryCache, zoneIds: Collection<Long>): String {
    val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    for (id in zoneIds) {
        val rings = geometryCache.getZoneRings(id) ?: continue
        if (rings.isEmpty()) continue
        if (!first) sb.append(",")
        first = false
        sb.append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(id).append("},\"geometry\":")
        if (rings.size == 1) {
            val cached = geometryCache.zoneDataAccessor(id)
            val poly = cached?.jtsPolygons?.firstOrNull()
            if (poly != null) {
                sb.append(polygonToGeoJson(poly))
            } else {
                sb.append("{\"type\":\"Polygon\",\"coordinates\":[")
                sb.append(coordArray(rings[0].outer.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()))
                sb.append("]}")
            }
        } else {
            sb.append("{\"type\":\"MultiPolygon\",\"coordinates\":[")
            for (i in rings.indices) {
                if (i > 0) sb.append(",")
                val cached = geometryCache.zoneDataAccessor(id)
                val poly = cached?.jtsPolygons?.getOrNull(i)
                sb.append("[")
                if (poly != null) {
                    sb.append(coordArray(poly.getExteriorRing().getCoordinates()))
                    for (h in 0 until poly.getNumInteriorRing()) {
                        sb.append(",")
                        sb.append(coordArray(poly.getInteriorRingN(h).getCoordinates()))
                    }
                } else {
                    sb.append(coordArray(rings[i].outer.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()))
                }
                sb.append("]")
            }
            sb.append("]}")
        }
        sb.append("}")
    }
    sb.append("]}")
    return sb.toString()
}

fun buildPoiGeoJson(pois: List<com.indiana.zwl.domain.model.Poi>): String {
    val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    for (poi in pois) {
        if (!first) sb.append(",")
        first = false
        val category = poi.classify().uiGroup().key
        sb.append("{\"type\":\"Feature\",\"properties\":{")
        sb.append("\"id\":").append(poi.id).append(",")
        sb.append("\"category\":\"").append(category).append("\",")
        sb.append("\"name\":\"").append(poi.name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",")
        sb.append("\"desc\":\"").append(poi.description.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",")
        sb.append("\"poiLat\":").append(poi.latitude).append(",")
        sb.append("\"poiLon\":").append(poi.longitude)
        sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
        sb.append(poi.longitude).append(",").append(poi.latitude)
        sb.append("]}}")
    }
    sb.append("]}")
    return sb.toString()
}

fun buildUserArrowGeoJson(latitude: Double, longitude: Double, heading: Float, scaleMeters: Double = 1.0): String {
    val halfLength = 12.0
    val halfWidth = 7.0
    val back = 8.0
    val notch = 4.0

    val tip = listOf(0.0 to halfLength)
    val baseLeft = listOf(-halfWidth to -back)
    val notchCenter = listOf(0.0 to -back + notch)
    val baseRight = listOf(halfWidth to -back)
    val shape = tip + baseLeft + notchCenter + baseRight

    val rad = Math.toRadians(heading.toDouble())
    val cos = Math.cos(rad)
    val sin = Math.sin(rad)
    val latRad = Math.toRadians(latitude)
    val metersPerDegLat = 111320.0
    val metersPerDegLng = 111320.0 * Math.cos(latRad)

    val coords = shape.map { (east, north) ->
        val se = east * scaleMeters
        val sn = north * scaleMeters
        val newEast = se * cos + sn * sin
        val newNorth = -se * sin + sn * cos
        val lng = longitude + newEast / metersPerDegLng
        val lat = latitude + newNorth / metersPerDegLat
        "$lng,$lat"
    }

    val sb = StringBuilder()
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[{")
    sb.append("\"type\":\"Feature\",\"properties\":{},\"geometry\":")
    sb.append("{\"type\":\"Polygon\",\"coordinates\":[[")
    for (i in coords.indices) {
        if (i > 0) sb.append(",")
        sb.append("[").append(coords[i]).append("]")
    }
    sb.append(",[").append(coords[0]).append("]")
    sb.append("]]}}]}")
    return sb.toString()
}

fun buildBanGeoJson(geometryCache: GeometryCache, banIds: Collection<Long>): String {
    val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    for (id in banIds) {
        val rings = geometryCache.getBanRings(id) ?: continue
        if (rings.isEmpty()) continue
        if (!first) sb.append(",")
        first = false
        sb.append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(id).append("},\"geometry\":")
        if (rings.size == 1) {
            val cached = geometryCache.banDataAccessor(id)
            val poly = cached?.jtsPolygons?.firstOrNull()
            if (poly != null) {
                sb.append(polygonToGeoJson(poly))
            } else {
                sb.append("{\"type\":\"Polygon\",\"coordinates\":[")
                sb.append(coordArray(rings[0].outer.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()))
                sb.append("]}")
            }
        } else {
            sb.append("{\"type\":\"MultiPolygon\",\"coordinates\":[")
            for (i in rings.indices) {
                if (i > 0) sb.append(",")
                val cached = geometryCache.banDataAccessor(id)
                val poly = cached?.jtsPolygons?.getOrNull(i)
                sb.append("[")
                if (poly != null) {
                    sb.append(coordArray(poly.getExteriorRing().getCoordinates()))
                    for (h in 0 until poly.getNumInteriorRing()) {
                        sb.append(",")
                        sb.append(coordArray(poly.getInteriorRingN(h).getCoordinates()))
                    }
                } else {
                    sb.append(coordArray(rings[i].outer.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()))
                }
                sb.append("]")
            }
            sb.append("]}")
        }
        sb.append("}")
    }
    sb.append("]}")
    return sb.toString()
}
