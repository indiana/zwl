package com.example.zwl.domain.util

import com.example.zwl.data.remote.model.GeoJsonGeometry
import com.google.gson.JsonArray
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel

object GeoJsonConverter {

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    fun toJtsGeometry(geoJsonGeom: GeoJsonGeometry): Geometry? {
        val type = geoJsonGeom.type
        val coordsElement = geoJsonGeom.coordinates

        if (!coordsElement.isJsonArray) return null
        val coordsArray = coordsElement.asJsonArray

        return when (type.lowercase()) {
            "polygon" -> parsePolygon(coordsArray)
            "multipolygon" -> parseMultiPolygon(coordsArray)
            else -> null
        }
    }

    private fun parsePolygon(jsonPolygon: JsonArray): Polygon? {
        if (jsonPolygon.size() == 0) return null

        val shellRing = parseLinearRing(jsonPolygon.get(0).asJsonArray) ?: return null
        val holes = ArrayList<LinearRing>()

        for (i in 1 until jsonPolygon.size()) {
            parseLinearRing(jsonPolygon.get(i).asJsonArray)?.let {
                holes.add(it)
            }
        }

        return geometryFactory.createPolygon(shellRing, holes.toTypedArray())
    }

    private fun parseMultiPolygon(jsonMultiPolygon: JsonArray): Geometry? {
        val polygons = ArrayList<Polygon>()
        for (i in 0 until jsonMultiPolygon.size()) {
            parsePolygon(jsonMultiPolygon.get(i).asJsonArray)?.let {
                polygons.add(it)
            }
        }
        if (polygons.isEmpty()) return null
        return geometryFactory.createMultiPolygon(polygons.toTypedArray())
    }

    private fun parseLinearRing(jsonRing: JsonArray): LinearRing? {
        if (jsonRing.size() < 4) return null

        val coords = ArrayList<Coordinate>()
        for (i in 0 until jsonRing.size()) {
            val coordPair = jsonRing.get(i).asJsonArray
            val lon = coordPair.get(0).asDouble
            val lat = coordPair.get(1).asDouble
            coords.add(Coordinate(lon, lat))
        }

        if (coords.first() != coords.last()) {
            coords.add(coords.first())
        }

        return geometryFactory.createLinearRing(coords.toTypedArray())
    }
}
