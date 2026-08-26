package com.indiana.zwl.presentation.map.util

import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import org.maplibre.android.geometry.LatLng

fun wktToFillLatLngs(wkt: String): List<List<LatLng>> {
    val wktReader = WKTReader()
    var geom = wktReader.read(wkt)!!
    if (!geom.isValid()) {
        try { geom = geom.buffer(0.0) } catch (_: Throwable) {}
    }

    val rings = mutableListOf<List<LatLng>>()
    val numGeoms = geom.getNumGeometries()
    for (g in 0 until numGeoms) {
        val subGeom = geom.getGeometryN(g)
        if (subGeom is Polygon) {
            val shell = subGeom.getExteriorRing()
            val shellLatLngs = shell.getCoordinates().map { c -> LatLng(c.getY(), c.getX()) }
            rings.add(shellLatLngs)

            for (h in 0 until subGeom.getNumInteriorRing()) {
                val hole = subGeom.getInteriorRingN(h)
                val holeLatLngs = hole.getCoordinates().map { c -> LatLng(c.getY(), c.getX()) }
                rings.add(holeLatLngs)
            }
        }
    }
    return rings
}

fun wktToJtsPolygon(wkt: String): Polygon? {
    val wktReader = WKTReader()
    var geom = wktReader.read(wkt) ?: return null
    if (!geom.isValid()) {
        try { geom = geom.buffer(0.0) } catch (_: Throwable) {}
    }
    val numGeoms = geom.getNumGeometries()
    for (g in 0 until numGeoms) {
        val sub = geom.getGeometryN(g)
        if (sub is Polygon) return sub
    }
    return null
}
