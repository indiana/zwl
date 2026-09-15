package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.Zone

interface ZoneRepository {
    suspend fun getAllZones(): List<Zone>
    suspend fun getZonesCount(): Int
    suspend fun getByForestDistrict(forestDistrict: String): Zone?
    suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long)
    suspend fun updateForestStand(forestDistrict: String, json: String, timestamp: Long)
    suspend fun insertAll(zones: List<Zone>)

    /**
     * Replaces the zone list with [zones] while carrying over the cached
     * columns (fire risk, forest stand) of zones that already existed —
     * a plain clear+insert used to wipe the offline fallback on every sync.
     */
    suspend fun syncAll(zones: List<Zone>)
    suspend fun clearAll()
}
