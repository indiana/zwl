package com.indiana.zwl.shared.data.repository

import com.indiana.zwl.domain.model.NewSavedPoint
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedPointRepositoryImpl(
    private val database: SharedDatabase
) : SavedPointRepository {

    override fun getAllPoints(): Flow<List<SavedPoint>> {
        return database.savedPointQueries.selectAll { id, name, latitude, longitude ->
            SavedPoint(
                id = id,
                name = name,
                latitude = latitude,
                longitude = longitude
            )
        }.asFlow().map { it.executeAsList() }
    }

    override suspend fun insert(point: NewSavedPoint): Long {
        database.savedPointQueries.insert(point.name, point.latitude, point.longitude)
        return database.savedPointQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun rename(id: Long, name: String) {
        database.savedPointQueries.renameById(name, id)
    }

    override suspend fun delete(id: Long) {
        database.savedPointQueries.deleteById(id)
    }

    override suspend fun deleteAll() {
        database.savedPointQueries.deleteAll()
    }
}
