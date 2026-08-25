package com.indiana.zwl.data.repository

import com.indiana.zwl.data.local.ForestBanDao
import com.indiana.zwl.data.local.ForestBanEntity
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.repository.ForestBanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ForestBanRepositoryImpl @Inject constructor(
    private val forestBanDao: ForestBanDao
) : ForestBanRepository {

    override suspend fun getAllBans(): List<ForestBan> = withContext(Dispatchers.IO) {
        forestBanDao.getAllBans().map { it.toDomainModel() }
    }

    override fun getAllBansFlow(): Flow<List<ForestBan>> {
        return forestBanDao.getAllBansFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getBansCount(): Int = withContext(Dispatchers.IO) {
        forestBanDao.getBansCount()
    }

    override suspend fun insertAll(bans: List<ForestBan>) {
        withContext(Dispatchers.IO) {
            forestBanDao.insertAll(bans.map { ban ->
                ForestBanEntity(
                    id = ban.id,
                    remoteId = ban.remoteId,
                    forestDistrictCode = ban.forestDistrictCode,
                    forestDistrictName = ban.forestDistrictName,
                    rdlpName = ban.rdlpName,
                    forestryName = ban.forestryName,
                    forestryCode = ban.forestryCode,
                    reason = ban.reason,
                    description = ban.description,
                    startDate = ban.startDate,
                    endDate = ban.endDate,
                    forestAddress = ban.forestAddress,
                    compartmentCode = ban.compartmentCode,
                    areaSqMeters = ban.areaSqMeters,
                    geometryWkt = ban.geometryWkt
                )
            })
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            forestBanDao.clearAll()
        }
    }
}
