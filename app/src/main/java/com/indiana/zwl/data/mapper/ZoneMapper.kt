package com.indiana.zwl.data.mapper

import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.domain.model.Zone

fun ZoneEntity.toDomainModel(): Zone {
    return Zone(
        id = this.id,
        forestDistrict = this.forestDistrict,
        geometryWkt = this.geometryWkt,
        fireRiskLevel = this.fireRiskLevel,
        fireRiskTimestamp = this.fireRiskTimestamp
    )
}

fun Zone.toEntity(): ZoneEntity {
    return ZoneEntity(
        id = this.id,
        forestDistrict = this.forestDistrict,
        geometryWkt = this.geometryWkt,
        fireRiskLevel = this.fireRiskLevel,
        fireRiskTimestamp = this.fireRiskTimestamp
    )
}
