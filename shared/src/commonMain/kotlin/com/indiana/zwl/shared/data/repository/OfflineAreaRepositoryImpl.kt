package com.indiana.zwl.shared.data.repository

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineAreaRepositoryImpl(
    private val database: SharedDatabase
) : OfflineAreaRepository {

    // SQLDelight maps plain INTEGER columns to Long; the domain API keeps
    // zooms/count as Int, so the mapper (and insert) convert at the boundary.
    private fun mapArea(
        id: Long,
        name: String,
        fileName: String,
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double,
        minZoom: Long,
        maxZoom: Long,
        tileCount: Long,
        fileSizeBytes: Long,
        downloadedAt: Long
    ): DownloadedArea = DownloadedArea(
        id = id,
        name = name,
        fileName = fileName,
        latSouth = latSouth,
        latNorth = latNorth,
        lonWest = lonWest,
        lonEast = lonEast,
        minZoom = minZoom.toInt(),
        maxZoom = maxZoom.toInt(),
        tileCount = tileCount.toInt(),
        fileSizeBytes = fileSizeBytes,
        downloadedAt = downloadedAt
    )

    override fun observeAll(): Flow<List<DownloadedArea>> {
        return database.downloadedAreaQueries.selectAll(::mapArea)
            .asFlow().map { it.executeAsList() }
    }

    override suspend fun getAll(): List<DownloadedArea> {
        return database.downloadedAreaQueries.selectAll(::mapArea).executeAsList()
    }

    override suspend fun insert(area: NewDownloadedArea): Long {
        database.downloadedAreaQueries.insert(
            name = area.name,
            fileName = area.fileName,
            latSouth = area.latSouth,
            latNorth = area.latNorth,
            lonWest = area.lonWest,
            lonEast = area.lonEast,
            minZoom = area.minZoom.toLong(),
            maxZoom = area.maxZoom.toLong(),
            tileCount = area.tileCount.toLong(),
            fileSizeBytes = area.fileSizeBytes,
            downloadedAt = area.downloadedAt
        )
        return database.downloadedAreaQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun rename(id: Long, name: String) {
        database.downloadedAreaQueries.renameById(name, id)
    }

    override suspend fun markRefreshed(
        id: Long,
        fileName: String,
        tileCount: Int,
        fileSizeBytes: Long,
        downloadedAt: Long
    ) {
        database.downloadedAreaQueries.markRefreshedById(
            fileName,
            tileCount.toLong(),
            fileSizeBytes,
            downloadedAt,
            id
        )
    }

    override suspend fun delete(id: Long) {
        database.downloadedAreaQueries.deleteById(id)
    }

    override suspend fun deleteAll() {
        database.downloadedAreaQueries.deleteAll()
    }
}
