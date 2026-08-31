package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import com.indiana.zwl.shared.data.remote.model.GeoJsonFeature
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal fun Map<String, JsonElement?>.toStringStringMap(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for ((key, value) in this) {
        val strValue = value?.jsonPrimitive?.contentOrNull
        if (!strValue.isNullOrBlank()) {
            result[key] = strValue
        }
    }
    return result
}

internal fun Throwable.logSwallowed(): Unit = println(stackTraceToString())

object ZoneSyncParser {
    fun parse(collection: GeoJsonCollection): List<Zone> {
        val zones = mutableListOf<Zone>()
        for (feature in collection.features) {
            try {
                val properties = feature.properties ?: continue
                val propsMap = properties.toStringStringMap()
                val wkt = GeoJsonToWkt.geometryToWkt(feature.geometry) ?: continue
                val forestDistrict = GeoJsonToWkt.extractForestDistrict(propsMap)
                val websiteUrl = GeoJsonToWkt.extractWebsiteUrl(propsMap)
                zones.add(
                    Zone(
                        id = 0,
                        forestDistrict = forestDistrict,
                        geometryWkt = wkt,
                        websiteUrl = websiteUrl
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.logSwallowed()
            }
        }
        return zones
    }
}

object ForestBanSyncParser {
    fun parse(collection: GeoJsonCollection): List<ForestBan> {
        val bans = mutableListOf<ForestBan>()
        for (feature in collection.features) {
            try {
                val properties = feature.properties ?: continue
                val propsMap = properties.toStringStringMap()
                val wkt = GeoJsonToWkt.geometryToWkt(feature.geometry) ?: continue
                bans.add(
                    ForestBan(
                        id = 0,
                        remoteId = propsMap["objectid"]?.toLongOrNull() ?: 0L,
                        forestDistrictCode = propsMap["kod_nadl"],
                        forestDistrictName = propsMap["nazwa_nadl"] ?: "Nadleśnictwo (Nieznane)",
                        rdlpName = propsMap["nazwa_rdlp"],
                        forestryName = propsMap["lesnictwo"],
                        forestryCode = propsMap["kod_lesn"]?.toIntOrNull(),
                        reason = propsMap["kod"] ?: "Zakaz wstepu do lasu",
                        description = propsMap["opis"],
                        startDate = propsMap["data"],
                        endDate = propsMap["data_koncowa"],
                        forestAddress = propsMap["adr_lesny"] ?: propsMap["adr_silp"],
                        compartmentCode = propsMap["kod_oddzialu"],
                        areaSqMeters = propsMap["st_area(shape)"]?.toDoubleOrNull(),
                        geometryWkt = wkt
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.logSwallowed()
            }
        }
        return bans
    }
}

object PoiSyncParser {
    fun parseFeatures(features: List<GeoJsonFeature>): List<Poi> {
        val pois = mutableListOf<Poi>()
        for (feature in features) {
            try {
                val properties = feature.properties
                val geom = feature.geometry

                if (geom.type.equals("point", ignoreCase = true) && geom.coordinates is JsonArray) {
                    val coords = geom.coordinates.jsonArray
                    if (coords.size >= 2) {
                        val lon = coords[0].jsonPrimitive.double
                        val lat = coords[1].jsonPrimitive.double

                        val code = properties?.get("tur_rec_pnt_cd")?.jsonPrimitive?.contentOrNull
                            ?: properties?.get("tur_edu_pnt_cd")?.jsonPrimitive?.contentOrNull
                            ?: properties?.get("tur_sleep_pnt_cd")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        val desc = properties?.get("tur_obj_desc")?.jsonPrimitive?.contentOrNull ?: ""
                        val name = properties?.get("nzw_ob")?.jsonPrimitive?.contentOrNull ?: ""

                        pois.add(
                            Poi(
                                id = 0,
                                code = code,
                                description = desc,
                                name = name,
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.logSwallowed()
            }
        }
        return pois
    }
}