package com.indiana.zwl.domain.repository

import com.indiana.zwl.data.local.PoiEntity
import kotlinx.coroutines.flow.Flow

interface PoiRepository {
    fun getAllPois(): Flow<List<PoiEntity>>
    suspend fun insertAll(pois: List<PoiEntity>)
    suspend fun clearAll()
}
