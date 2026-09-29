package com.indiana.zwl.shared.data.water

import com.indiana.zwl.domain.model.DrinkingWaterStatus
import com.indiana.zwl.domain.model.WaterSource
import com.indiana.zwl.domain.model.WaterSourceType
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import com.indiana.zwl.shared.data.remote.model.GeoJsonFeature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object WaterSourceParser {

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(text: String, json: Json = defaultJson): List<WaterSource> {
        val collection = json.decodeFromString<GeoJsonCollection>(text)
        return collection.features.mapNotNull { it.toWaterSource() }
    }

    fun parseBytes(bytes: ByteArray, json: Json = defaultJson): List<WaterSource> =
        parse(bytes.decodeToString(), json)

    private fun GeoJsonFeature.toWaterSource(): WaterSource? {
        if (geometry.type != "Point") return null
        val coordinates = geometry.coordinates as? JsonArray ?: return null
        if (coordinates.size < 2) return null
        val longitude = coordinates[0].asDoubleOrNull() ?: return null
        val latitude = coordinates[1].asDoubleOrNull() ?: return null

        val props = properties ?: return null
        val osmId = props["osmId"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val type = WaterSourceType.entries
            .firstOrNull { it.name == props["type"].asStringOrNull() }
            ?: return null
        val drinkingWater = DrinkingWaterStatus.entries
            .firstOrNull { it.name == props["drinkingWater"].asStringOrNull() }
            ?: DrinkingWaterStatus.UNKNOWN

        return WaterSource(
            id = 0L,
            osmId = osmId,
            type = type,
            name = props["name"].asStringOrNull().orEmpty(),
            latitude = latitude,
            longitude = longitude,
            source = WaterDataConfig.SOURCE_OSM,
            drinkingWater = drinkingWater,
            verified = props["verified"].asBooleanOrNull() ?: false,
            depthMeters = props["depthMeters"].asDoubleOrNull(),
            notes = props["notes"].asStringOrNull(),
            pump = props["pump"].asNonBlankStringOrNull(),
            drinkingWaterRaw = props["drinkingWaterRaw"].asNonBlankStringOrNull(),
            seasonal = props["seasonal"].asNonBlankStringOrNull(),
            intermittent = props["intermittent"].asNonBlankStringOrNull(),
            fountain = props["fountain"].asNonBlankStringOrNull(),
            fee = props["fee"].asNonBlankStringOrNull(),
            openingHours = props["openingHours"].asNonBlankStringOrNull(),
            operator = props["operator"].asNonBlankStringOrNull(),
            description = props["description"].asNonBlankStringOrNull(),
            bottle = props["bottle"].asNonBlankStringOrNull()
        )
    }
}

private fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
}

private fun JsonElement?.asNonBlankStringOrNull(): String? =
    asStringOrNull()?.takeIf { it.isNotBlank() }

private fun JsonElement?.asDoubleOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.toDoubleOrNull()
}

private fun JsonElement?.asBooleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return when (primitive.content.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}
