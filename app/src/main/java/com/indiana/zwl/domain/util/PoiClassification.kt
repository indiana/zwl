package com.indiana.zwl.domain.util

import com.indiana.zwl.data.local.PoiEntity

enum class PoiCategory { SHELTER, FIREPLACE, OTHER }

fun PoiEntity.classify(): PoiCategory {
    val nameLower = name.lowercase(java.util.Locale.getDefault())
    return when {
        nameLower.contains("wiata") || nameLower.contains("altan") ||
            nameLower.contains("szałas") || nameLower.contains("shelter") -> PoiCategory.SHELTER
        nameLower.contains("ognis") || nameLower.contains("palenis") ||
            nameLower.contains("fire") -> PoiCategory.FIREPLACE
        else -> PoiCategory.OTHER
    }
}

fun PoiCategory.displayName(): String = when (this) {
    PoiCategory.SHELTER -> "Wiata turystyczna / Schronienie"
    PoiCategory.FIREPLACE -> "Miejsce na ognisko / Palenisko"
    PoiCategory.OTHER -> "Inne"
}
