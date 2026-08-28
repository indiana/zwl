package com.indiana.zwl.domain.usecase

import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.model.FireRiskFeature
import com.indiana.zwl.shared.data.remote.model.FireRiskGeoJson
import com.indiana.zwl.shared.data.remote.model.FireRiskProperties
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
class GetFireRiskUseCaseTest {

    private val fireApi: BdlFireApi = mockk()
    private lateinit var getFireRiskUseCase: GetFireRiskUseCase

    @Before
    fun setUp() {
        getFireRiskUseCase = GetFireRiskUseCase(fireApi)
    }

    @Test
    fun `invoke should return success code when API returns valid response`() = runTest {
        val mockResponse = FireRiskGeoJson(
            features = listOf(
                FireRiskFeature(
                    properties = FireRiskProperties(kod = 2.0, opis = "Srednie zagrozenie")
                )
            )
        )
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns mockResponse

        val result = getFireRiskUseCase(53.62, 21.32)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        coVerify(exactly = 1) { fireApi.getFireHazard(geometry = "21.32,53.62") }
    }

    @Test
    fun `invoke should return success -1 when API returns features empty list`() = runTest {
        val mockResponse = FireRiskGeoJson(features = emptyList())
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns mockResponse

        val result = getFireRiskUseCase(53.62, 21.32)

        assertTrue(result.isSuccess)
        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `invoke should return success -1 when API returns features null`() = runTest {
        val mockResponse = FireRiskGeoJson(features = null)
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns mockResponse

        val result = getFireRiskUseCase(53.62, 21.32)

        assertTrue(result.isSuccess)
        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `invoke should return failure when API throws exception`() = runTest {
        val exception = IOException("Network error")
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws exception

        val result = getFireRiskUseCase(53.62, 21.32)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
