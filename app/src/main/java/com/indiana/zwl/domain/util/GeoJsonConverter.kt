package com.indiana.zwl.domain.util

import com.indiana.zwl.data.remote.model.GeoJsonGeometry
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

    fun parseFeatureCollectionStream(
        reader: java.io.Reader,
        onFeatureParsed: (properties: Map<String, String>, geometry: Geometry) -> Unit
    ) {
        val jsonReader = com.google.gson.stream.JsonReader(reader)
        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val name = jsonReader.nextName()
            if (name == "features") {
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    parseFeature(jsonReader, onFeatureParsed)
                }
                jsonReader.endArray()
            } else {
                jsonReader.skipValue()
            }
        }
        jsonReader.endObject()
    }

    private fun parseFeature(
        reader: com.google.gson.stream.JsonReader,
        onFeatureParsed: (properties: Map<String, String>, geometry: Geometry) -> Unit
    ) {
        reader.beginObject()
        var properties: Map<String, String>? = null
        var geometry: Geometry? = null

        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "properties" -> {
                    properties = parseProperties(reader)
                }
                "geometry" -> {
                    geometry = parseGeometry(reader)
                }
                else -> {
                    reader.skipValue()
                }
            }
        }
        reader.endObject()

        if (properties != null && geometry != null) {
            onFeatureParsed(properties, geometry)
        }
    }

    private fun parseProperties(reader: com.google.gson.stream.JsonReader): Map<String, String> {
        val props = mutableMapOf<String, String>()
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return props
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
                reader.nextNull()
            } else {
                props[key] = reader.nextString()
            }
        }
        reader.endObject()
        return props
    }

    private fun parseGeometry(reader: com.google.gson.stream.JsonReader): Geometry? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        reader.beginObject()
        var type: String? = null
        var coordinatesData: Any? = null

        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "type" -> {
                    type = reader.nextString()
                }
                "coordinates" -> {
                    coordinatesData = parseAnyCoordinates(reader)
                }
                else -> {
                    reader.skipValue()
                }
            }
        }
        reader.endObject()

        if (type != null && coordinatesData != null) {
            return createGeometry(type, coordinatesData)
        }
        return null
    }

    private fun parseAnyCoordinates(reader: com.google.gson.stream.JsonReader): Any {
        reader.beginArray()
        val peekToken = reader.peek()
        val result: Any
        if (peekToken == com.google.gson.stream.JsonToken.NUMBER) {
            val lon = reader.nextDouble()
            val lat = reader.nextDouble()
            while (reader.hasNext()) {
                reader.skipValue()
            }
            result = Coordinate(lon, lat)
        } else {
            val list = mutableListOf<Any>()
            while (reader.hasNext()) {
                list.add(parseAnyCoordinates(reader))
            }
            result = list
        }
        reader.endArray()
        return result
    }

    private fun createGeometry(type: String, coordinatesData: Any): Geometry? {
        return when (type.lowercase()) {
            "polygon" -> {
                @Suppress("UNCHECKED_CAST")
                val rings = coordinatesData as? List<List<Coordinate>> ?: return null
                createPolygon(rings)
            }
            "multipolygon" -> {
                @Suppress("UNCHECKED_CAST")
                val polysList = coordinatesData as? List<List<List<Coordinate>>> ?: return null
                val polygons = polysList.mapNotNull { createPolygon(it) }
                if (polygons.isEmpty()) null else geometryFactory.createMultiPolygon(polygons.toTypedArray())
            }
            else -> null
        }
    }

    private fun createPolygon(rings: List<List<Coordinate>>): Polygon? {
        if (rings.isEmpty()) return null
        val shellRing = createLinearRing(rings[0]) ?: return null
        val holes = ArrayList<LinearRing>()
        for (i in 1 until rings.size) {
            createLinearRing(rings[i])?.let { holes.add(it) }
        }
        return geometryFactory.createPolygon(shellRing, holes.toTypedArray())
    }

    private fun createLinearRing(coordsList: List<Coordinate>): LinearRing? {
        if (coordsList.size < 4) return null
        val coords = ArrayList(coordsList)
        if (coords.first() != coords.last()) {
            coords.add(coords.first())
        }
        return geometryFactory.createLinearRing(coords.toTypedArray())
    }

    fun extractForestDistrict(properties: Map<String, String>): String {
        val linkValue = properties["link"]
        if (!linkValue.isNullOrBlank()) {
            try {
                val urlStr = if (!linkValue.startsWith("http://") && !linkValue.startsWith("https://")) {
                    "https://$linkValue"
                } else {
                    linkValue
                }
                val uri = java.net.URI(urlStr)
                val host = uri.host ?: ""
                val parts = host.split(".")
                val namePart = parts.firstOrNull { it != "www" && it.isNotBlank() }
                if (!namePart.isNullOrBlank()) {
                    return "Nadleśnictwo " + namePart.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val keys = listOf("nadlesnictw", "nadlesnictwo", "nazwa_nadl", "nazwa", "nadl", "district", "nzw_ob")
        for (key in keys) {
            val value = properties[key]
            if (!value.isNullOrBlank()) return value
        }
        return "Nadleśnictwo (Nieznane)"
    }
}
