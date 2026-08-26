package com.indiana.zwl.shared.data.repository

import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ZoneRepositoryImpl(
    private val database: SharedDatabase
) : ZoneRepository {

    override suspend fun getAllZones(): List<Zone> {
        return database.zoneQueries.selectAll { id, forestDistrict, geometryWkt, fireRiskLevel, fireRiskTimestamp, forestStandJson, forestStandTimestamp, websiteUrl ->
            Zone(
                id = id,
                forestDistrict = forestDistrict,
                geometryWkt = geometryWkt,
                fireRiskLevel = fireRiskLevel?.toInt(),
                fireRiskTimestamp = fireRiskTimestamp,
                forestStandJson = forestStandJson,
                forestStandTimestamp = forestStandTimestamp,
                websiteUrl = websiteUrl
            )
        }.executeAsList()
    }

    override suspend fun getZonesCount(): Int {
        return database.zoneQueries.selectCount().executeAsOne().toInt()
    }

    override suspend fun getByForestDistrict(forestDistrict: String): Zone? {
        return database.zoneQueries.selectByForestDistrict(forestDistrict) { id, forestDistrict_, geometryWkt, fireRiskLevel, fireRiskTimestamp, forestStandJson, forestStandTimestamp, websiteUrl ->
            Zone(
                id = id,
                forestDistrict = forestDistrict_,
                geometryWkt = geometryWkt,
                fireRiskLevel = fireRiskLevel?.toInt(),
                fireRiskTimestamp = fireRiskTimestamp,
                forestStandJson = forestStandJson,
                forestStandTimestamp = forestStandTimestamp,
                websiteUrl = websiteUrl
            )
        }.executeAsOneOrNull()
    }

    override suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long) {
        database.zoneQueries.updateFireRisk(fireRiskLevel.toLong(), timestamp, forestDistrict)
    }

    override suspend fun updateForestStand(forestDistrict: String, json: String, timestamp: Long) {
        database.zoneQueries.updateForestStand(json, timestamp, forestDistrict)
    }

    override suspend fun insertAll(zones: List<Zone>) {
        zones.forEach { zone ->
            database.zoneQueries.insertAll(
                zone.id,
                zone.forestDistrict,
                zone.geometryWkt,
                zone.fireRiskLevel?.toLong(),
                zone.fireRiskTimestamp,
                zone.forestStandJson,
                zone.forestStandTimestamp,
                zone.websiteUrl
            )
        }
    }

    override suspend fun clearAll() {
        database.zoneQueries.deleteAll()
    }
}
