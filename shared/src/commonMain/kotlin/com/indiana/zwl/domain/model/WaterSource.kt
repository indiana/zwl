package com.indiana.zwl.domain.model

enum class WaterSourceType {
    DRINKING_WATER, WATER_TAP, WATER_POINT, SPRING, WELL, FOUNTAIN, WATER_ON_SITE, REFILL
}

enum class DrinkingWaterStatus { YES, NO, UNKNOWN }

data class WaterSource(
    val id: Long,
    val osmId: String,
    val type: WaterSourceType,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val source: String,
    val drinkingWater: DrinkingWaterStatus,
    val verified: Boolean,
    val depthMeters: Double? = null,
    val notes: String? = null,
    val pump: String? = null,
    val drinkingWaterRaw: String? = null,
    val seasonal: String? = null,
    val intermittent: String? = null,
    val fountain: String? = null,
    val fee: String? = null,
    val openingHours: String? = null,
    val operator: String? = null,
    val description: String? = null,
    val bottle: String? = null
)

data class WaterDataState(
    val dataVersion: Long,
    val lastCheckedAt: Long,
    val sha256: String?,
    val count: Long
)
