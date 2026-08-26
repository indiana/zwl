package com.indiana.zwl.shared.data.repository

import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.shared.data.local.SharedDatabase
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PoiRepositoryImpl(
    private val database: SharedDatabase
) : PoiRepository {

    override fun getAllPois(): Flow<List<Poi>> {
        return database.poiQueries.selectAll { id, code, description, name, latitude, longitude ->
            Poi(
                id = id,
                code = code,
                description = description,
                name = name,
                latitude = latitude,
                longitude = longitude
            )
        }.asFlow().map { it.executeAsList() }
    }

    override suspend fun insertAll(pois: List<Poi>) {
        pois.forEach { poi ->
            database.poiQueries.insertAll(
                poi.id,
                poi.code,
                poi.description,
                poi.name,
                poi.latitude,
                poi.longitude
            )
        }
    }

    override suspend fun clearAll() {
        database.poiQueries.deleteAll()
    }
}
