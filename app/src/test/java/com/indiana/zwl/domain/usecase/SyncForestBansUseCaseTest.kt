package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SyncForestBansUseCaseTest {

    private val arcgisApi: BdlArcgisApi = mockk()
    private val forestBanRepository: ForestBanRepository = mockk(relaxed = true)
    private lateinit var syncForestBansUseCase: SyncForestBansUseCase

    @Before
    fun setUp() {
        syncForestBansUseCase = SyncForestBansUseCase(arcgisApi, forestBanRepository)
    }

    @Test
    fun `invoke should fetch forest bans from API and insert them into database`() = runTest {
        // Arrange
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "objectid": 123,
                    "kod_nadl": "012345",
                    "nazwa_nadl": "Nadleśnictwo Kudypy",
                    "nazwa_rdlp": "RDLP Szczecinek",
                    "lesnictwo": "Leśnictwo Borne",
                    "kod_lesn": "15",
                    "kod": "Zakaz wstępu",
                    "opis": "Ochrona przyrody",
                    "data": "2024-01-01",
                    "data_koncowa": "2024-06-30",
                    "adr_lesny": "Działka 15A",
                    "kod_oddzialu": "100a",
                    "st_area(shape)": 5000.5
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

        coEvery { arcgisApi.getForestBans() } returns geoJson

        // Act
        val result = syncForestBansUseCase()

        // Assert
        assertTrue(result.isSuccess)
        val bans = result.getOrThrow()
        assertEquals(1, bans.size)
        assertEquals("Nadleśnictwo Kudypy", bans[0].forestDistrictName)
        assertEquals("012345", bans[0].forestDistrictCode)
        assertEquals("RDLP Szczecinek", bans[0].rdlpName)
        assertEquals("Leśnictwo Borne", bans[0].forestryName)
        assertEquals("Zakaz wstępu", bans[0].reason)
        assertEquals("Ochrona przyrody", bans[0].description)
        assertEquals("2024-01-01", bans[0].startDate)
        assertEquals("2024-06-30", bans[0].endDate)
        assertEquals("Działka 15A", bans[0].forestAddress)
        assertEquals("100a", bans[0].compartmentCode)

        coVerify(exactly = 1) { forestBanRepository.clearAll() }
        coVerify(exactly = 1) { forestBanRepository.insertAll(any()) }
    }

    @Test
    fun `invoke should return failure when API returns empty features`() = runTest {
        // Arrange
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": []
            }
        """.trimIndent()

        coEvery { arcgisApi.getForestBans() } returns geoJson

        // Act
        val result = syncForestBansUseCase()

        // Assert
        assertTrue(result.isSuccess)
        val bans = result.getOrThrow()
        assertEquals(0, bans.size)
        coVerify(exactly = 1) { forestBanRepository.clearAll() }
        coVerify(exactly = 0) { forestBanRepository.insertAll(any()) }
    }

    @Test
    fun `invoke should return failure when API call throws exception`() = runTest {
        // Arrange
        val exception = IOException("Network error")
        coEvery { arcgisApi.getForestBans() } throws exception

        // Act
        val result = syncForestBansUseCase()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 0) { forestBanRepository.clearAll() }
        coVerify(exactly = 0) { forestBanRepository.insertAll(any()) }
    }
}
