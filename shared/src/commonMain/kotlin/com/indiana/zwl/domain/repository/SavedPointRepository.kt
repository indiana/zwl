package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.NewSavedPoint
import com.indiana.zwl.domain.model.SavedPoint
import kotlinx.coroutines.flow.Flow

interface SavedPointRepository {
    fun getAllPoints(): Flow<List<SavedPoint>>
    suspend fun insert(point: NewSavedPoint): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
