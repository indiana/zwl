package com.indiana.zwl.domain.model

data class ForestStandSummary(
    val speciesBreakdown: List<SpeciesEntry>,
    val forestFunction: String?,
    val standStructure: String?,
    val siteType: String?,
    val protectionCategory: String?,
    val totalAreaHa: Double,
    val rotationAge: Int?
)

data class SpeciesEntry(
    val speciesCode: String,
    val speciesName: String,
    val percentage: Double,
    val ageClass: AgeClass
)

enum class AgeClass(val labelPl: String) {
    MLODY("młody"),
    DOJRZALY("dojrzały"),
    STARY("stary"),
    BRAK_DANYCH("brak danych")
}
