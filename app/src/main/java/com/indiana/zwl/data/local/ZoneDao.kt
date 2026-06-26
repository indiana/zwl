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

    @Query("SELECT COUNT(*) FROM zones")
    suspend fun getZonesCount(): Int

    @Query("DELETE FROM zones")
    suspend fun clearAll()

    @Query("UPDATE zones SET fireRiskLevel = :fireRiskLevel, fireRiskTimestamp = :timestamp WHERE forestDistrict = :forestDistrict")
    suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long)

    @Query("UPDATE zones SET fireRiskLevel = :fireRiskLevel, fireRiskTimestamp = :timestamp WHERE id = :zoneId")
    suspend fun updateFireRiskById(zoneId: Long, fireRiskLevel: Int, timestamp: Long)
}
