package com.indiana.zwl.domain.repository

import com.indiana.zwl.domain.model.ForestBan
import kotlinx.coroutines.flow.Flow

interface ForestBanRepository {
    suspend fun getAllBans(): List<ForestBan>
    fun getAllBansFlow(): Flow<List<ForestBan>>
    suspend fun getBansCount(): Int
    suspend fun insertAll(bans: List<ForestBan>)
    suspend fun clearAll()
}
