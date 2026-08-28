package com.indiana.zwl.shared.map

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.util.PoiCategory
import com.indiana.zwl.domain.util.classify
import com.indiana.zwl.shared.data.remote.model.GeoJsonGeometry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds GeoJSON FeatureCollection strings for the map layers. The Swift map
 * parses these strings into shape sources instead of consuming Kotlin models
 * directly, keeping the bridge small and stable.
 */
object MapGeoJson {

    fun zonesToGeoJson(zones: List<Zone>): String {
        val features = zones.mapNotNull { zone ->
            val geometry = WktToGeoJson.geometryToGeoJson(zone.geometryWkt) ?: return@mapNotNull null
            feature(
                geometry = geometry,
                properties = buildJsonObject {
                    put("id", zone.id)
                    put("name", zone.forestDistrict)
                    zone.websiteUrl?.let { put("websiteUrl", it) }
                }
            )
        }
        return collection(features)
    }

    fun bansToGeoJson(bans: List<ForestBan>): String {
        val features = bans.mapNotNull { ban ->
            val geometry = WktToGeoJson.geometryToGeoJson(ban.geometryWkt) ?: return@mapNotNull null
            feature(
                geometry = geometry,
                properties = buildJsonObject {
                    put("remoteId", ban.remoteId)
                    put("id", ban.id)
                    put("forestDistrictName", ban.forestDistrictName)
                    put("reason", ban.reason)
                    ban.forestDistrictCode?.let { put("forestDistrictCode", it) }
                    ban.rdlpName?.let { put("rdlpName", it) }
                    ban.forestryName?.let { put("forestryName", it) }
                    ban.forestryCode?.let { put("forestryCode", it) }
                    ban.description?.let { put("description", it) }
                    ban.startDate?.let { put("startDate", it) }
                    ban.endDate?.let { put("endDate", it) }
                    ban.forestAddress?.let { put("forestAddress", it) }
                    ban.compartmentCode?.let { put("compartmentCode", it) }
                    ban.areaSqMeters?.let { put("areaSqMeters", it) }
                }
            )
        }
        return collection(features)
    }

    fun poisToGeoJson(pois: List<Poi>): String {
        val features = pois.map { poi ->
            feature(
                geometry = GeoJsonGeometry(
                    type = "Point",
                    coordinates = buildJsonArray {
                        add(JsonPrimitive(poi.longitude))
                        add(JsonPrimitive(poi.latitude))
                    }
                ),
                properties = buildJsonObject {
                    put("id", poi.id)
                    put("name", poi.name)
                    put("description", poi.description)
                    put("code", poi.code)
                    put("categoryKey", poi.classify().categoryKey())
                }
            )
        }
        return collection(features)
    }

    private fun PoiCategory.categoryKey(): String = when (this) {
        PoiCategory.SHELTER -> "shelter"
        PoiCategory.FIREPLACE -> "fireplace"
        PoiCategory.OTHER -> "other"
    }

    private fun feature(geometry: GeoJsonGeometry, properties: JsonObject): JsonObject {
        return buildJsonObject {
            put("type", "Feature")
            put("properties", properties)
            put("geometry", buildJsonObject {
                put("type", geometry.type)
                put("coordinates", geometry.coordinates)
            })
        }
    }

    private fun collection(features: List<JsonObject>): String {
        val root = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }
        return root.toString()
    }
}