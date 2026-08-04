package com.indiana.zwl.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ZoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<ZoneEntity>)

    @Query("SELECT * FROM zones")
    suspend fun getAllZones(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE forestDistrict = :forestDistrict COLLATE NOCASE LIMIT 1")
    suspend fun getByForestDistrict(forestDistrict: String): ZoneEntity?

    @Query("SELECT COUNT(*) FROM zones")
    suspend fun getZonesCount(): Int

    @Query("DELETE FROM zones")
    suspend fun clearAll()

    @Query("UPDATE zones SET fireRiskLevel = :fireRiskLevel, fireRiskTimestamp = :timestamp WHERE forestDistrict = :forestDistrict")
    suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long)

    @Query("UPDATE zones SET forestStandJson = :json, forestStandTimestamp = :timestamp WHERE forestDistrict = :forestDistrict")
    suspend fun updateForestStand(forestDistrict: String, json: String, timestamp: Long)
}
