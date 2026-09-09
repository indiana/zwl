package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.model.NewDownloadedArea
import kotlinx.coroutines.flow.Flow

interface OfflineAreaRepository {
    fun observeAll(): Flow<List<DownloadedArea>>
    suspend fun getAll(): List<DownloadedArea>
    suspend fun insert(area: NewDownloadedArea): Long
    suspend fun rename(id: Long, name: String)
    suspend fun markRefreshed(id: Long, fileName: String, tileCount: Int, fileSizeBytes: Long, downloadedAt: Long)
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
