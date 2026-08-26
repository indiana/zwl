package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import com.indiana.zwl.shared.data.remote.model.GeoJsonFeature
import com.indiana.zwl.shared.data.remote.model.GeoJsonGeometry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
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

    private fun makeTestCollection(): GeoJsonCollection {
        return GeoJsonCollection(
            type = "FeatureCollection",
            features = listOf(
                GeoJsonFeature(
                    type = "Feature",
                    properties = mapOf(
                        "objectid" to JsonPrimitive(123),
                        "kod_nadl" to JsonPrimitive("012345"),
                        "nazwa_nadl" to JsonPrimitive("Nadleśnictwo Kudypy"),
                        "nazwa_rdlp" to JsonPrimitive("RDLP Szczecinek"),
                        "lesnictwo" to JsonPrimitive("Leśnictwo Borne"),
                        "kod_lesn" to JsonPrimitive("15"),
                        "kod" to JsonPrimitive("Zakaz wstępu"),
                        "opis" to JsonPrimitive("Ochrona przyrody"),
                        "data" to JsonPrimitive("2024-01-01"),
                        "data_koncowa" to JsonPrimitive("2024-06-30"),
                        "adr_lesny" to JsonPrimitive("Działka 15A"),
                        "kod_oddzialu" to JsonPrimitive("100a"),
                        "st_area(shape)" to JsonPrimitive(5000.5)
                    ),
                    geometry = GeoJsonGeometry(
                        type = "Polygon",
                        coordinates = kotlinx.serialization.json.Json.parseToJsonElement(
                            "[[[19.123,52.123],[19.124,52.123],[19.124,52.124],[19.123,52.123]]]"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `invoke should fetch forest bans from API and insert them into database`() = runTest {
        coEvery { arcgisApi.getForestBans(any(), any()) } returns makeTestCollection()

        val result = syncForestBansUseCase()

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
    fun `invoke should return empty list when API returns empty features`() = runTest {
        coEvery { arcgisApi.getForestBans(any(), any()) } returns GeoJsonCollection(
            type = "FeatureCollection",
            features = emptyList()
        )

        val result = syncForestBansUseCase()

        assertTrue(result.isSuccess)
        val bans = result.getOrThrow()
        assertEquals(0, bans.size)
        coVerify(exactly = 1) { forestBanRepository.clearAll() }
        coVerify(exactly = 0) { forestBanRepository.insertAll(any()) }
    }

    @Test
    fun `invoke should return failure when API call throws exception`() = runTest {
        val exception = IOException("Network error")
        coEvery { arcgisApi.getForestBans(any(), any()) } throws exception

        val result = syncForestBansUseCase()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 0) { forestBanRepository.clearAll() }
        coVerify(exactly = 0) { forestBanRepository.insertAll(any()) }
    }
}
