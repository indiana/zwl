package com.indiana.zwl.domain.model

enum class WaterSourceType { DRINKING_WATER, WATER_TAP, WATER_POINT, SPRING, WELL, FOUNTAIN }

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
    val notes: String? = null
)

data class WaterDataState(
    val dataVersion: Long,
    val lastCheckedAt: Long,
    val sha256: String?,
    val count: Long
)
