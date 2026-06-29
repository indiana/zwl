package com.indiana.zwl.domain

import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.model.LocationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class SpatialEngineTest {

    private lateinit var spatialEngine: SpatialEngine

    @Before
    fun setUp() {
        spatialEngine = SpatialEngine()
    }

    @Test
    fun testSpatialEngineCalculations() {
        val wktSpychowo = "POLYGON ((21.30 53.60, 21.35 53.60, 21.35 53.65, 21.30 53.65, 21.30 53.60))"
        val zones = listOf(
            Zone(id = 1, forestDistrict = "Nadleśnictwo Spychowo", geometryWkt = wktSpychowo)
        )

        spatialEngine.initialize(zones)

        val statusInside = spatialEngine.checkLocation(53.62, 21.32)
        assertTrue(statusInside is LocationStatus.InZone)
        assertEquals("Nadleśnictwo Spychowo", (statusInside as LocationStatus.InZone).forestDistrict)

        val statusOutside = spatialEngine.checkLocation(53.62, 21.28)
        assertTrue(statusOutside is LocationStatus.OutsideZone)
        val outside = statusOutside as LocationStatus.OutsideZone
        assertEquals("Nadleśnictwo Spychowo", outside.nearestDistrict)
        
        assertTrue("Odległość powinna być bliska 1320m, wynosi: ${outside.distanceMeters}", 
            outside.distanceMeters in 1280.0..1360.0)

        assertEquals(90f, outside.bearingDegrees, 1.0f)
    }

    @Test
    fun testPerformanceNfr2() {
        val zones = (1..100).map { id ->
            Zone(
                id = id.toLong(),
                forestDistrict = "Nadleśnictwo $id",
                geometryWkt = "POLYGON ((${21.30 + id * 0.01} 53.60, ${21.35 + id * 0.01} 53.60, ${21.35 + id * 0.01} 53.65, ${21.30 + id * 0.01} 53.65, ${21.30 + id * 0.01} 53.60))"
            )
        }

        spatialEngine.initialize(zones)

        val executionTime = measureTimeMillis {
            for (i in 0 until 500) {
                spatialEngine.checkLocation(53.62, 21.32)
            }
        }

        val averageTime = executionTime / 500.0
        println("Średni czas zapytania przestrzennego: $averageTime ms")
        assertTrue("Średni czas zapytania ($averageTime ms) powinien być mniejszy niż 10ms", averageTime < 10.0)
    }
}
