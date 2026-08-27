package com.indiana.zwl.shared.data.repository

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ForestBanRepositoryImpl(
    private val database: SharedDatabase
) : ForestBanRepository {

    override suspend fun getAllBans(): List<ForestBan> {
        return database.forestBanQueries.selectAll { id, remoteId, forestDistrictCode, forestDistrictName, rdlpName, forestryName, forestryCode, reason, description, startDate, endDate, forestAddress, compartmentCode, areaSqMeters, geometryWkt ->
            ForestBan(
                id = id,
                remoteId = remoteId,
                forestDistrictCode = forestDistrictCode?.trim(),
                forestDistrictName = forestDistrictName?.trim() ?: "Nadleśnictwo (Nieznane)",
                rdlpName = rdlpName?.trim(),
                forestryName = forestryName?.trim(),
                forestryCode = forestryCode?.toInt(),
                reason = reason?.trim() ?: "Zakaz wstępu do lasu",
                description = description?.trim(),
                startDate = startDate?.trim(),
                endDate = endDate?.trim(),
                forestAddress = forestAddress?.trim(),
                compartmentCode = compartmentCode?.trim(),
                areaSqMeters = areaSqMeters,
                geometryWkt = geometryWkt
            )
        }.executeAsList()
    }

    override fun getAllBansFlow(): Flow<List<ForestBan>> {
        return database.forestBanQueries.selectAll { id, remoteId, forestDistrictCode, forestDistrictName, rdlpName, forestryName, forestryCode, reason, description, startDate, endDate, forestAddress, compartmentCode, areaSqMeters, geometryWkt ->
            ForestBan(
                id = id,
                remoteId = remoteId,
                forestDistrictCode = forestDistrictCode?.trim(),
                forestDistrictName = forestDistrictName?.trim() ?: "Nadleśnictwo (Nieznane)",
                rdlpName = rdlpName?.trim(),
                forestryName = forestryName?.trim(),
                forestryCode = forestryCode?.toInt(),
                reason = reason?.trim() ?: "Zakaz wstępu do lasu",
                description = description?.trim(),
                startDate = startDate?.trim(),
                endDate = endDate?.trim(),
                forestAddress = forestAddress?.trim(),
                compartmentCode = compartmentCode?.trim(),
                areaSqMeters = areaSqMeters,
                geometryWkt = geometryWkt
            )
        }.asFlow().map { it.executeAsList() }
    }

    override suspend fun insertAll(bans: List<ForestBan>) {
        bans.forEach { ban ->
            database.forestBanQueries.insertAll(
                ban.remoteId,
                ban.forestDistrictCode,
                ban.forestDistrictName,
                ban.rdlpName,
                ban.forestryName,
                ban.forestryCode?.toLong(),
                ban.reason,
                ban.description,
                ban.startDate,
                ban.endDate,
                ban.forestAddress,
                ban.compartmentCode,
                ban.areaSqMeters,
                ban.geometryWkt
            )
        }
    }

    override suspend fun clearAll() {
        database.forestBanQueries.deleteAll()
    }
}
