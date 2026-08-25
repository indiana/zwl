package com.indiana.zwl.data.mapper

import com.indiana.zwl.data.local.ForestBanEntity
import com.indiana.zwl.domain.model.ForestBan

fun ForestBanEntity.toDomainModel(): ForestBan {
    return ForestBan(
        id = this.id,
        remoteId = this.remoteId,
        forestDistrictCode = this.forestDistrictCode?.trim(),
        forestDistrictName = this.forestDistrictName?.trim() ?: "Nadleśnictwo (Nieznane)",
        rdlpName = this.rdlpName?.trim(),
        forestryName = this.forestryName?.trim(),
        forestryCode = this.forestryCode,
        reason = this.reason?.trim() ?: "Zakaz wstępu do lasu",
        description = this.description?.trim(),
        startDate = this.startDate?.trim(),
        endDate = this.endDate?.trim(),
        forestAddress = this.forestAddress?.trim(),
        compartmentCode = this.compartmentCode?.trim(),
        areaSqMeters = this.areaSqMeters,
        geometryWkt = this.geometryWkt
    )
}
