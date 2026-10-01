package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.WaterDataState
import com.indiana.zwl.domain.model.WaterSource
import kotlinx.coroutines.flow.Flow

interface WaterSourceRepository {
    fun getAll(): Flow<List<WaterSource>>
    suspend fun getAllOnce(): List<WaterSource>
    suspend fun count(): Long
    suspend fun replaceAll(sources: List<WaterSource>)
    suspend fun clearAll()
    suspend fun getDataState(): WaterDataState?
    suspend fun setDataState(version: Long, lastCheckedAt: Long, sha256: String?, count: Long)
    suspend fun setLastCheckedAt(timestampMillis: Long)
}
