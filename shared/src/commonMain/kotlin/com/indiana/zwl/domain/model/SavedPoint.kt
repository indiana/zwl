package com.indiana.zwl.domain.model

data class SavedPoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class NewSavedPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
)
