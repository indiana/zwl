package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.WaterSourceType

enum class WaterSourceGroup { DRINKING, SPRING, WELL }

fun WaterSourceType.waterGroup(): WaterSourceGroup = when (this) {
    WaterSourceType.DRINKING_WATER,
    WaterSourceType.WATER_TAP,
    WaterSourceType.WATER_POINT,
    WaterSourceType.FOUNTAIN,
    WaterSourceType.WATER_ON_SITE,
    WaterSourceType.REFILL -> WaterSourceGroup.DRINKING
    WaterSourceType.SPRING -> WaterSourceGroup.SPRING
    WaterSourceType.WELL -> WaterSourceGroup.WELL
}

/**
 * Polish label for a water source type. Single source of truth for both
 * platforms' detail cards and the nearby-points list.
 */
fun WaterSourceType.displayLabel(): String = when (this) {
    WaterSourceType.DRINKING_WATER -> "Woda pitna"
    WaterSourceType.WATER_TAP -> "Kran z wodą"
    WaterSourceType.WATER_POINT -> "Punkt poboru wody"
    WaterSourceType.SPRING -> "Źródło"
    WaterSourceType.WELL -> "Studnia"
    WaterSourceType.FOUNTAIN -> "Fontanna"
    WaterSourceType.WATER_ON_SITE -> "Woda na miejscu (biwak/wiata)"
    WaterSourceType.REFILL -> "Punkt napełniania butelek"
}
