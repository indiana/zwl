package com.indiana.zwl.data.repository

import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.ZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ZoneRepositoryImpl @Inject constructor(
    private val zoneDao: ZoneDao
) : ZoneRepository {

    override suspend fun getAllZones(): List<Zone> = withContext(Dispatchers.IO) {
        zoneDao.getAllZones().map { it.toDomainModel() }
    }

    override suspend fun getZonesCount(): Int = withContext(Dispatchers.IO) {
        zoneDao.getZonesCount()
    }

    override suspend fun getByForestDistrict(forestDistrict: String): Zone? = withContext(Dispatchers.IO) {
        zoneDao.getByForestDistrict(forestDistrict)?.toDomainModel()
    }

    override suspend fun updateFireRisk(forestDistrict: String, fireRiskLevel: Int, timestamp: Long) {
        withContext(Dispatchers.IO) {
            zoneDao.updateFireRisk(forestDistrict, fireRiskLevel, timestamp)
        }
    }

    override suspend fun updateForestStand(forestDistrict: String, json: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            zoneDao.updateForestStand(forestDistrict, json, timestamp)
        }
    }

    override suspend fun insertAll(zones: List<Zone>) {
        withContext(Dispatchers.IO) {
            zoneDao.insertAll(zones.map { zone ->
                ZoneEntity(
                    id = zone.id,
                    forestDistrict = zone.forestDistrict,
                    geometryWkt = zone.geometryWkt,
                    fireRiskLevel = zone.fireRiskLevel,
                    fireRiskTimestamp = zone.fireRiskTimestamp,
                    forestStandJson = zone.forestStandJson,
                    forestStandTimestamp = zone.forestStandTimestamp,
                    websiteUrl = zone.websiteUrl
                )
            })
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            zoneDao.clearAll()
        }
    }
}
