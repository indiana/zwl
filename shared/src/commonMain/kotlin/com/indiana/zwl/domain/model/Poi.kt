package com.indiana.zwl.domain.model

data class Poi(
    val id: Long,
    val code: String,
    val description: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
