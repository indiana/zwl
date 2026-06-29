package com.indiana.zwl.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import com.indiana.zwl.data.local.PoiDao
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.data.remote.model.GeoJsonCollection
import com.indiana.zwl.data.remote.model.GeoJsonFeature
import com.indiana.zwl.data.remote.model.GeoJsonGeometry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPoiUseCaseTest {

    private val arcgisApi: BdlArcgisApi = mockk()
    private val poiDao: PoiDao = mockk(relaxed = true)
    private lateinit var syncPoiUseCase: SyncPoiUseCase

    @Before
    fun setUp() {
        syncPoiUseCase = SyncPoiUseCase(arcgisApi, poiDao)
    }

    private fun createMockGeometry(lon: Double, lat: Double): GeoJsonGeometry {
        val coords = JsonArray().apply {
            add(JsonPrimitive(lon))
            add(JsonPrimitive(lat))
        }
        return GeoJsonGeometry(type = "Point", coordinates = coords)
    }

    @Test
    fun `invoke should fetch all 4 layers and insert features into database`() = runTest {
        // Arrange
        val layers = listOf(1, 2, 3, 4)
        for (layerId in layers) {
            val mockResponse = GeoJsonCollection(
                type = "FeatureCollection",
                features = listOf(
                    GeoJsonFeature(
                        type = "Feature",
                        properties = mapOf(
                            "tur_rec_pnt_cd" to "MSC WYPOCZ",
                            "tur_obj_desc" to "Miejsce wypoczynku dla warstwy $layerId",
                            "nzw_ob" to "Wiata $layerId"
                        ),
                        geometry = createMockGeometry(21.0 + layerId, 52.0 + layerId)
                    )
                )
            )
            coEvery {
                arcgisApi.getTouristPoints(
                    layerId = layerId,
                    resultOffset = 0,
                    resultRecordCount = 2000
                )
            } returns mockResponse

            // Mock subsequent empty response for pagination loop termination
            coEvery {
                arcgisApi.getTouristPoints(
                    layerId = layerId,
                    resultOffset = 2000,
                    resultRecordCount = 2000
                )
            } returns GeoJsonCollection(type = "FeatureCollection", features = emptyList())
        }

        // Act
        val result = syncPoiUseCase()

        // Assert
        assertTrue(result.isSuccess)
        
        val capturedEntities = mutableListOf<List<PoiEntity>>()
        coVerify(exactly = 1) { poiDao.clearAll() }
        coVerify(exactly = 1) { poiDao.insertAll(capture(capturedEntities)) }

        val insertedList = capturedEntities.first()
        assertEquals(4, insertedList.size)
        
        // Verify values
        for (i in 0..3) {
            val layerId = i + 1
            val entity = insertedList[i]
            assertEquals("MSC WYPOCZ", entity.code)
            assertEquals("Miejsce wypoczynku dla warstwy $layerId", entity.description)
            assertEquals("Wiata $layerId", entity.name)
            assertEquals(52.0 + layerId, entity.latitude, 0.001)
            assertEquals(21.0 + layerId, entity.longitude, 0.001)
        }
    }

    @Test
    fun `invoke should return failure when API throws exception`() = runTest {
        // Arrange
        val exception = IOException("API error")
        coEvery {
            arcgisApi.getTouristPoints(
                layerId = any(),
                resultOffset = any(),
                resultRecordCount = any()
            )
        } throws exception

        // Act
        val result = syncPoiUseCase()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 0) { poiDao.clearAll() }
        coVerify(exactly = 0) { poiDao.insertAll(any()) }
    }

    @Test
    fun `invoke should return failure when no features are retrieved across all layers`() = runTest {
        // Arrange
        coEvery {
            arcgisApi.getTouristPoints(
                layerId = any(),
                resultOffset = any(),
                resultRecordCount = any()
            )
        } returns GeoJsonCollection(type = "FeatureCollection", features = emptyList())

        // Act
        val result = syncPoiUseCase()

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { poiDao.clearAll() }
        coVerify(exactly = 0) { poiDao.insertAll(any()) }
    }
}
