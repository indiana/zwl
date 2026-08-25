package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetZonesUseCase(
    private val zoneRepository: ZoneRepository
) {
    suspend operator fun invoke(): List<Zone> = withContext(Dispatchers.IO) {
        zoneRepository.getAllZones()
    }
}
