package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.SpeciesEntry
import com.indiana.zwl.domain.model.TranslatedCode
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.model.FireRiskFeature
import com.indiana.zwl.shared.data.remote.model.FireRiskGeoJson
import com.indiana.zwl.shared.data.remote.model.FireRiskProperties
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoadPointDetailsUseCaseTest {

    private val fireApi: BdlFireApi = mockk()
    private val forestStandForPoint: GetForestStandForPointUseCase = mockk()
    private lateinit var loadPointDetailsUseCase: LoadPointDetailsUseCase
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        loadPointDetailsUseCase = LoadPointDetailsUseCase(
            GetFireRiskUseCase(fireApi),
            forestStandForPoint
        )
    }

    private fun responseWithKod(kod: Double?) = FireRiskGeoJson(
        features = listOf(
            FireRiskFeature(properties = FireRiskProperties(kod = kod, opis = "test"))
        )
    )

    private val summaryWithSoil = ForestStandSummary(
        speciesBreakdown = listOf(
            SpeciesEntry(speciesCode = "SO", speciesName = "Sosna", percentage = 80.0, ageLabel = "40 lat")
        ),
        forestFunction = TranslatedCode(code = "O SPO", name = "Las ochronny"),
        standStructure = null,
        siteType = TranslatedCode(code = "BMŚW", name = "Bór mieszany świeży"),
        protectionCategory = null,
        totalAreaHa = 1.5,
        rotationAge = 100,
        soilType = TranslatedCode(code = "RD", name = "Rdzawy"),
        groundCover = TranslatedCode(code = "MSZ", name = "Mszary")
    )

    @Test
    fun `invoke returns fire risk and forest stand on success`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns responseWithKod(2.0)
        coEvery { forestStandForPoint(53.62, 21.32) } returns Result.success(summaryWithSoil)

        val result = loadPointDetailsUseCase(53.62, 21.32, now)

        assertEquals(2, result.fireRiskLevel)
        assertEquals(summaryWithSoil, result.forestStand)
        assertEquals("Rdzawy", result.forestStand?.soilType?.name)
        assertEquals("Mszary", result.forestStand?.groundCover?.name)
    }

    @Test
    fun `invoke keeps the stand when fire risk is offline`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws IOException("no network")
        coEvery { forestStandForPoint(53.62, 21.32) } returns Result.success(summaryWithSoil)

        val result = loadPointDetailsUseCase(53.62, 21.32, now)

        assertEquals(-1, result.fireRiskLevel)
        assertEquals(summaryWithSoil, result.forestStand)
    }

    @Test
    fun `invoke returns -2 when the fire service has no data`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns
            FireRiskGeoJson(features = emptyList())
        coEvery { forestStandForPoint(53.62, 21.32) } returns Result.success(summaryWithSoil)

        val result = loadPointDetailsUseCase(53.62, 21.32, now)

        assertEquals(-2, result.fireRiskLevel)
    }

    @Test
    fun `invoke returns a null stand when the stand lookup fails`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns responseWithKod(3.0)
        coEvery { forestStandForPoint(53.62, 21.32) } returns
            Result.failure(IOException("stand failed"))

        val result = loadPointDetailsUseCase(53.62, 21.32, now)

        assertEquals(3, result.fireRiskLevel)
        assertNull(result.forestStand)
    }

    @Test
    fun `invoke returns a null stand when the stand lookup throws`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns responseWithKod(1.0)
        coEvery { forestStandForPoint(53.62, 21.32) } throws IOException("stand threw")

        val result = loadPointDetailsUseCase(53.62, 21.32, now)

        assertEquals(1, result.fireRiskLevel)
        assertNull(result.forestStand)
    }
}
