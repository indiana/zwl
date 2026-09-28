package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.WaterSourceType

enum class WaterSourceGroup { DRINKING, SPRING, WELL }

fun WaterSourceType.waterGroup(): WaterSourceGroup = when (this) {
    WaterSourceType.DRINKING_WATER,
    WaterSourceType.WATER_TAP,
    WaterSourceType.WATER_POINT,
    WaterSourceType.FOUNTAIN -> WaterSourceGroup.DRINKING
    WaterSourceType.SPRING -> WaterSourceGroup.SPRING
    WaterSourceType.WELL -> WaterSourceGroup.WELL
}
