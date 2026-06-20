package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.mapper.toDomainModel
import com.indiana.zwl.domain.model.Zone
import javax.inject.Inject

class GetZonesUseCase @Inject constructor(
    private val zoneDao: ZoneDao
) {
    suspend operator fun invoke(): List<Zone> {
        return zoneDao.getAllZones().map { it.toDomainModel() }
    }
}
