package com.indiana.zwl.domain.model

import kotlinx.serialization.Serializable

/**
 * Raw BDL code + its translated (Polish) display name.
 * [code] is exactly what the BDL API returned (e.g. "O SPO", "2 PIĘT", "ŚW.KB")
 * and is the lookup key for tooltips / Wikipedia links in [com.indiana.zwl.domain.util.BdlInfo].
 */
@Serializable
data class TranslatedCode(
    val code: String,
    val name: String
)

@Serializable
data class ForestStandSummary(
    val speciesBreakdown: List<SpeciesEntry>,
    val forestFunction: TranslatedCode?,
    val standStructure: TranslatedCode?,
    val siteType: TranslatedCode?,
    val protectionCategory: TranslatedCode?,
    val totalAreaHa: Double,
    val rotationAge: Int?,
    // SILP taxation description of the wydzielenie at the anchor point
    // (saved point / map click inside a zone). Null when unknown — e.g.
    // cached summaries from before this field existed.
    val soilType: TranslatedCode? = null,
    val groundCover: TranslatedCode? = null
)

@Serializable
data class SpeciesEntry(
    val speciesCode: String,
    val speciesName: String,
    val percentage: Double,
    val ageLabel: String?
)

/**
 * SILP taxation-description extras read at a point: soil type (typ gleby) and
 * ground cover (pokrywa) of the wydzielenie under it.
 */
data class SoilCover(
    val soilType: TranslatedCode?,
    val groundCover: TranslatedCode?
)
