package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ForestBanDao
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.domain.model.ForestBan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetForestBansUseCase @Inject constructor(
    private val forestBanDao: ForestBanDao
) {
    suspend operator fun invoke(): List<ForestBan> = withContext(Dispatchers.IO) {
        forestBanDao.getAllBans().map { it.toDomainModel() }
    }

    fun asFlow(): Flow<List<ForestBan>> {
        return forestBanDao.getAllBansFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
}
