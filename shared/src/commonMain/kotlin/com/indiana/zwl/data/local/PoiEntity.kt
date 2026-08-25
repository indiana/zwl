package com.indiana.zwl.data.local

data class PoiEntity(
    val id: Long = 0,
    val code: String,
    val description: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
