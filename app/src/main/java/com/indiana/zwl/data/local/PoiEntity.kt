package com.indiana.zwl.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val description: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
