package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.model.ForestBan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetForestBansUseCase @Inject constructor(
    private val forestBanRepository: ForestBanRepository
) {
    suspend operator fun invoke(): List<ForestBan> = withContext(Dispatchers.IO) {
        forestBanRepository.getAllBans()
    }

    fun asFlow(): Flow<List<ForestBan>> {
        return forestBanRepository.getAllBansFlow()
    }
}
