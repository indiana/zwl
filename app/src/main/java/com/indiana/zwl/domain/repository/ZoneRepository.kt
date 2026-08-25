package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.Zone

interface ZoneRepository {
    suspend fun getAllZones(): List<Zone>
    suspend fun getZonesCount(): Int
    suspend fun getByForestDistrict(forestDistrict: String): Zone?
    suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long)
    suspend fun updateForestStand(forestDistrict: String, json: String, timestamp: Long)
    suspend fun insertAll(zones: List<Zone>)
    suspend fun clearAll()
}
