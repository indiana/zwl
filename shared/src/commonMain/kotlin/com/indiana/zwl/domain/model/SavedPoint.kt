package com.indiana.zwl.domain.model

data class SavedPoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    // Epoch millis when the point was saved. 0 for points saved before this
    // field existed (migrated as NULL); the UI hides the date for those.
    val createdAt: Long = 0L,
    // Zone-detail style caches (same semantics as on Zone): last fire-risk
    // code read for the point coordinates and the JSON-encoded
    // ForestStandSummary of the point's bbox, both with 24h freshness
    // timestamps. Null until the point's properties were opened once.
    val fireRiskLevel: Int? = null,
    val fireRiskTimestamp: Long? = null,
    val forestStandJson: String? = null,
    val forestStandTimestamp: Long? = null
)

data class NewSavedPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
)
