package com.indiana.zwl.data.mapper

import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneMapperTest {

    @Test
    fun `toDomainModel should map ZoneEntity to Zone correctly`() {
        // Arrange
        val entity = ZoneEntity(
            id = 42L,
            forestDistrict = "Nadleśnictwo Borki",
            geometryWkt = "POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10))",
            fireRiskLevel = 1,
            fireRiskTimestamp = 123456789L
        )

        // Act
        val domain = entity.toDomainModel()

        // Assert
        assertEquals(42L, domain.id)
        assertEquals("Nadleśnictwo Borki", domain.forestDistrict)
        assertEquals("POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10))", domain.geometryWkt)
        assertEquals(1, domain.fireRiskLevel)
        assertEquals(123456789L, domain.fireRiskTimestamp)
    }

    @Test
    fun `toEntity should map Zone to ZoneEntity correctly`() {
        // Arrange
        val domain = Zone(
            id = 77L,
            forestDistrict = "Nadleśnictwo Spychowo",
            geometryWkt = "POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))",
            fireRiskLevel = 3,
            fireRiskTimestamp = 987654321L
        )

        // Act
        val entity = domain.toEntity()

        // Assert
        assertEquals(77L, entity.id)
        assertEquals("Nadleśnictwo Spychowo", entity.forestDistrict)
        assertEquals("POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))", entity.geometryWkt)
        assertEquals(3, entity.fireRiskLevel)
        assertEquals(987654321L, entity.fireRiskTimestamp)
    }
}
