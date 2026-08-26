package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.Poi
import kotlinx.coroutines.flow.Flow

interface PoiRepository {
    fun getAllPois(): Flow<List<Poi>>
    suspend fun insertAll(pois: List<Poi>)
    suspend fun clearAll()
}
