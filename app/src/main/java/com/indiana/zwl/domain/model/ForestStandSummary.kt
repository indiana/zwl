package com.indiana.zwl.domain.model

/**
 * Raw BDL code + its translated (Polish) display name.
 * [code] is exactly what the BDL API returned (e.g. "O SPO", "2 PIĘT", "ŚW.KB")
 * and is the lookup key for tooltips / Wikipedia links in [com.indiana.zwl.domain.util.BdlInfo].
 */
data class TranslatedCode(
    val code: String,
    val name: String
)

data class ForestStandSummary(
    val speciesBreakdown: List<SpeciesEntry>,
    val forestFunction: TranslatedCode?,
    val standStructure: TranslatedCode?,
    val siteType: TranslatedCode?,
    val protectionCategory: TranslatedCode?,
    val totalAreaHa: Double,
    val rotationAge: Int?
)

data class SpeciesEntry(
    val speciesCode: String,
    val speciesName: String,
    val percentage: Double,
    val ageLabel: String?
)
