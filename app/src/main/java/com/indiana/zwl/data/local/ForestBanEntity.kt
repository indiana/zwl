package com.indiana.zwl.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forest_bans")
data class ForestBanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long,
    val forestDistrictCode: String?,
    val forestDistrictName: String?,
    val rdlpName: String?,
    val forestryName: String?,
    val forestryCode: Int?,
    val reason: String?,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val forestAddress: String?,
    val compartmentCode: String?,
    val areaSqMeters: Double?,
    val geometryWkt: String
)
