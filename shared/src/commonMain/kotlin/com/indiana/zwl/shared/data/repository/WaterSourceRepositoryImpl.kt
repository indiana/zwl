package com.indiana.zwl.shared.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.indiana.zwl.domain.model.DrinkingWaterStatus
import com.indiana.zwl.domain.model.WaterDataState
import com.indiana.zwl.domain.model.WaterSource
import com.indiana.zwl.domain.model.WaterSourceType
import com.indiana.zwl.domain.repository.WaterSourceRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WaterSourceRepositoryImpl(
    private val database: SharedDatabase
) : WaterSourceRepository {

    override fun getAll(): Flow<List<WaterSource>> {
        return database.waterSourceQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getAllOnce(): List<WaterSource> {
        return database.waterSourceQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun count(): Long {
        return database.waterSourceQueries.count().executeAsOne()
    }

    override suspend fun replaceAll(sources: List<WaterSource>) {
        database.transaction {
            database.waterSourceQueries.deleteAll()
            sources.forEach { source ->
                database.waterSourceQueries.insertAll(
                    osmId = source.osmId,
                    type = source.type.name,
                    name = source.name,
                    latitude = source.latitude,
                    longitude = source.longitude,
                    source = source.source,
                    drinkingWater = source.drinkingWater.name,
                    verified = if (source.verified) 1L else 0L,
                    depthMeters = source.depthMeters,
                    notes = source.notes
                )
            }
        }
    }

    override suspend fun clearAll() {
        database.waterSourceQueries.deleteAll()
    }

    override suspend fun getDataState(): WaterDataState? {
        return database.waterDataStateQueries.select()
            .executeAsOneOrNull()
            ?.let { row ->
                WaterDataState(
                    dataVersion = row.dataVersion,
                    lastCheckedAt = row.lastCheckedAt,
                    sha256 = row.sha256,
                    count = row.count
                )
            }
    }

    override suspend fun setDataState(version: Long, lastCheckedAt: Long, sha256: String?, count: Long) {
        database.waterDataStateQueries.upsert(
            dataVersion = version,
            lastCheckedAt = lastCheckedAt,
            sha256 = sha256,
            count = count
        )
    }

    override suspend fun setLastCheckedAt(timestampMillis: Long) {
        val state = getDataState()
        database.waterDataStateQueries.upsert(
            dataVersion = state?.dataVersion ?: 0L,
            lastCheckedAt = timestampMillis,
            sha256 = state?.sha256,
            count = state?.count ?: 0L
        )
    }

    private fun com.indiana.zwl.shared.data.local.WaterSource.toDomain(): WaterSource {
        return WaterSource(
            id = id,
            osmId = osmId,
            type = WaterSourceType.entries.firstOrNull { it.name == type }
                ?: WaterSourceType.WATER_POINT,
            name = name,
            latitude = latitude,
            longitude = longitude,
            source = source,
            drinkingWater = DrinkingWaterStatus.entries.firstOrNull { it.name == drinkingWater }
                ?: DrinkingWaterStatus.UNKNOWN,
            verified = verified != 0L,
            depthMeters = depthMeters,
            notes = notes
        )
    }
}
