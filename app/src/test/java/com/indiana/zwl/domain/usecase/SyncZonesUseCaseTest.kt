package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.local.ZoneEntity
import com.indiana.zwl.data.remote.BdlArcgisApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SyncZonesUseCaseTest {

    private val arcgisApi: BdlArcgisApi = mockk()
    private val zoneDao: ZoneDao = mockk(relaxed = true)
    private lateinit var syncZonesUseCase: SyncZonesUseCase

    @Before
    fun setUp() {
        syncZonesUseCase = SyncZonesUseCase(arcgisApi, zoneDao)
    }

    @Test
    fun `invoke should fetch zones from API and insert them into database`() = runTest {
        // Arrange
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "link": "kudypy.szczecinek.lasy.gov.pl",
                    "nzw_ob": "Kudypy"
                  },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [
                        [19.123, 52.123],
                        [19.124, 52.123],
                        [19.124, 52.124],
                        [19.123, 52.123]
                      ]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaTypeOrNull()
        val responseBody = geoJson.toResponseBody(mediaType)

        coEvery { arcgisApi.getZanocujWLesieZones() } returns responseBody

        // Act
        val result = syncZonesUseCase()

        // Assert
        assertTrue(result.isSuccess)
        val zones = result.getOrThrow()
        assertEquals(1, zones.size)
        assertEquals("Nadleśnictwo Kudypy", zones[0].forestDistrict)

        val capturedEntities = mutableListOf<List<ZoneEntity>>()
        coVerify(exactly = 1) { zoneDao.clearAll() }
        coVerify(exactly = 1) { zoneDao.insertAll(capture(capturedEntities)) }

        val insertedList = capturedEntities.first()
        assertEquals(1, insertedList.size)
        assertEquals("Nadleśnictwo Kudypy", insertedList[0].forestDistrict)
        assertTrue(insertedList[0].geometryWkt.contains("POLYGON"))
    }

    @Test
    fun `invoke should return failure when API returns empty zones`() = runTest {
        // Arrange
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": []
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaTypeOrNull()
        val responseBody = geoJson.toResponseBody(mediaType)

        coEvery { arcgisApi.getZanocujWLesieZones() } returns responseBody

        // Act
        val result = syncZonesUseCase()

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { zoneDao.clearAll() }
        coVerify(exactly = 0) { zoneDao.insertAll(any()) }
    }

    @Test
    fun `invoke should return failure when API call throws exception`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        coEvery { arcgisApi.getZanocujWLesieZones() } throws exception

        // Act
        val result = syncZonesUseCase()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 0) { zoneDao.clearAll() }
        coVerify(exactly = 0) { zoneDao.insertAll(any()) }
    }
}
