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
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class GetFireRiskUseCaseTest {

    private val fireApi: BdlFireApi = mockk()
    private lateinit var getFireRiskUseCase: GetFireRiskUseCase
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        getFireRiskUseCase = GetFireRiskUseCase(fireApi)
    }

    private fun responseWithKod(kod: Double?) = FireRiskGeoJson(
        features = listOf(
            FireRiskFeature(properties = FireRiskProperties(kod = kod, opis = "test"))
        )
    )

    @Test
    fun `invoke returns the live code when the API returns a valid 0-3 kod`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns responseWithKod(2.0)

        val result = getFireRiskUseCase(53.62, 21.32, now)

        assertEquals(2, result)
        coVerify(exactly = 1) { fireApi.getFireHazard(geometry = "21.32,53.62") }
    }

    @Test
    fun `invoke returns -2 when the API returns an empty feature list`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns
            FireRiskGeoJson(features = emptyList())

        assertEquals(-2, getFireRiskUseCase(53.62, 21.32, now))
    }

    @Test
    fun `invoke returns -2 when the feature has no kod`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } returns responseWithKod(null)

        assertEquals(-2, getFireRiskUseCase(53.62, 21.32, now))
    }

    @Test
    fun `invoke returns -1 on a transient failure without a cache`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws IOException("no network")

        assertEquals(-1, getFireRiskUseCase(53.62, 21.32, now, cached = {
            GetFireRiskUseCase.Cached(null, null)
        }))
    }

    @Test
    fun `invoke returns the cached code plus ten on a transient failure with a fresh cache`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws IOException("no network")

        val result = getFireRiskUseCase(
            53.62,
            21.32,
            now,
            cached = { GetFireRiskUseCase.Cached(1, now - 60L * 60 * 1000) }
        )

        assertEquals(11, result)
    }

    @Test
    fun `invoke returns -1 on a transient failure with a stale cache`() = runTest {
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws IOException("no network")

        val result = getFireRiskUseCase(
            53.62,
            21.32,
            now,
            cached = { GetFireRiskUseCase.Cached(1, now - 25L * 60 * 60 * 1000) }
        )

        assertEquals(-1, result)
    }

    @Test
    fun `invoke returns -2 and reports a non-transient failure`() = runTest {
        val exception = IllegalArgumentException("bad request")
        coEvery { fireApi.getFireHazard(geometry = "21.32,53.62") } throws exception
        var reported: Throwable? = null

        val result = getFireRiskUseCase(
            53.62,
            21.32,
            now,
            cached = { GetFireRiskUseCase.Cached(3, now) },
            onNonTransientError = { reported = it }
        )

        assertEquals(-2, result)
        assertSame(exception, reported)
    }
}
