package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.Poi

/**
 * Category of a POI, derived from the authoritative BDL tourist-point code
 * (`tur_rec_pnt_cd` / `tur_edu_pnt_cd` / `tur_sleep_pnt_cd`), falling back to
 * secondary name matching only for `MSC WYPOCZ` ("Miejsca wypoczynku"), where
 * the BDL code does not distinguish shelters from fireplaces.
 */
enum class PoiCategory {
    ACCOMMODATION, CAMPING, SHELTER, FIREPLACE, REST,
    PLAYGROUND, VIEWPOINT, WATER, PARKING, EDUCATION, OTHER
}

/**
 * Top-level UI toggle groups. Multiple [PoiCategory] values map onto a single
 * [PoiUiGroup] so the filter checkboxes stay manageable (7 groups).
 */
enum class PoiUiGroup(val key: String, val label: String) {
    ACCOMMODATION("noclegi", "Noclegi i biwakowanie"),
    REST("wypoczynek", "Miejsca wypoczynku"),
    SHELTER("wiaty", "Wiaty i schronienia"),
    FIREPLACE("ogniska", "Miejsca na ognisko"),
    VIEWPOINT("widoki", "Punkty widokowe i rekreacja"),
    PARKING("parkingi", "Parkingi"),
    EDUCATION("edukacja", "Edukacja leśna"),
    OTHER("inne", "Inne")
}

fun Poi.classify(): PoiCategory {
    return when (code) {
        "SCHRONISKO", "HOTEL", "POK GOSC", "OS SZK WYP", "KWAT MYSL" -> PoiCategory.ACCOMMODATION
        "BIWAK", "MSC BIWAK" -> PoiCategory.CAMPING
        "MSC WYPOCZ" -> classifyRest()
        "PL ZABAW" -> PoiCategory.PLAYGROUND
        "PKT WIDOK" -> PoiCategory.VIEWPOINT
        "PT WODOW" -> PoiCategory.WATER
        "PARKING", "MSC POST" -> PoiCategory.PARKING
        "IZB ED LES", "O ED EKOL", "ZIEL KLAS" -> PoiCategory.EDUCATION
        else -> PoiCategory.OTHER
    }
}

private fun Poi.classifyRest(): PoiCategory {
    val nameLower = name.lowercase()
    return when {
        nameLower.contains("wiata") || nameLower.contains("altan") ||
            nameLower.contains("szałas") || nameLower.contains("shelter") -> PoiCategory.SHELTER
        nameLower.contains("ognis") || nameLower.contains("palenis") ||
            nameLower.contains("fire") -> PoiCategory.FIREPLACE
        else -> PoiCategory.REST
    }
}

fun PoiCategory.uiGroup(): PoiUiGroup = when (this) {
    PoiCategory.ACCOMMODATION, PoiCategory.CAMPING -> PoiUiGroup.ACCOMMODATION
    PoiCategory.REST -> PoiUiGroup.REST
    PoiCategory.SHELTER -> PoiUiGroup.SHELTER
    PoiCategory.FIREPLACE -> PoiUiGroup.FIREPLACE
    PoiCategory.PLAYGROUND, PoiCategory.VIEWPOINT, PoiCategory.WATER -> PoiUiGroup.VIEWPOINT
    PoiCategory.PARKING -> PoiUiGroup.PARKING
    PoiCategory.EDUCATION -> PoiUiGroup.EDUCATION
    PoiCategory.OTHER -> PoiUiGroup.OTHER
}

fun PoiUiGroup.displayName(): String = label

/** Display name for a POI, using its UI group label. */
fun Poi.displayGroupName(): String = classify().uiGroup().displayName()
