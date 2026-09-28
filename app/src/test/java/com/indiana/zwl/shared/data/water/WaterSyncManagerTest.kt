package com.indiana.zwl.shared.data.water

import com.indiana.zwl.domain.model.WaterDataState
import com.indiana.zwl.domain.repository.WaterSourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WaterSyncManagerTest {

    private val api: WaterDataApi = mockk()
    private val repository: WaterSourceRepository = mockk(relaxed = true)
    private val baselineProvider: WaterBaselineProvider = mockk()
    private lateinit var manager: WaterSyncManager

    private val json = Json { ignoreUnknownKeys = true }

    private val dataJson =
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"osmId":"n1","type":"SPRING","name":"","drinkingWater":"UNKNOWN","verified":false,"depthMeters":null,"notes":null},"geometry":{"type":"Point","coordinates":[19.0,52.0]}}]}"""
    private val dataBytes = dataJson.encodeToByteArray()
    private val dataSha = Sha256.hex(dataBytes)

    @Before
    fun setUp() {
        manager = WaterSyncManager(api, repository, baselineProvider, json)
    }

    private fun manifest(
        version: Long,
        bytes: Long = dataBytes.size.toLong(),
        sha: String = dataSha
    ) = WaterManifest(
        version = version,
        generatedAt = "2026-09-28T00:00:00Z",
        file = "water-$version.geojson",
        sha256 = sha,
        bytes = bytes,
        count = 1L,
        sources = listOf("OSM")
    )

    @Test
    fun `loads the bundled baseline when the database is empty`() = runTest {
        coEvery { repository.count() } returns 0L
        coEvery { baselineProvider.load() } returns dataBytes

        val result = manager.ensureBaselineIfEmpty()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.replaceAll(any()) }
        coVerify(exactly = 1) {
            repository.setDataState(
                version = WaterDataConfig.BASELINE_VERSION,
                lastCheckedAt = 0L,
                sha256 = null,
                count = 1L
            )
        }
    }

    @Test
    fun `baseline is a no-op when data is already present`() = runTest {
        coEvery { repository.count() } returns 5L

        val result = manager.ensureBaselineIfEmpty()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { baselineProvider.load() }
        coVerify(exactly = 0) { repository.replaceAll(any()) }
    }

    @Test
    fun `baseline fails when the bundled asset is missing`() = runTest {
        coEvery { repository.count() } returns 0L
        coEvery { baselineProvider.load() } returns null

        assertTrue(manager.ensureBaselineIfEmpty().isFailure)
        coVerify(exactly = 0) { repository.replaceAll(any()) }
    }

    @Test
    fun `throttles refresh within 24 hours without touching the network`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(
            dataVersion = 20260901L,
            lastCheckedAt = PlatformClock.nowMillis() - 1_000L,
            sha256 = null,
            count = 1L
        )

        val result = manager.refreshIfStale()

        assertEquals(WaterSyncOutcome.Skipped, result.getOrNull())
        coVerify(exactly = 0) { api.fetchManifest(any()) }
        coVerify(exactly = 0) { repository.setLastCheckedAt(any()) }
    }

    @Test
    fun `force bypasses the throttle`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(
            dataVersion = 20260901L,
            lastCheckedAt = PlatformClock.nowMillis(),
            sha256 = null,
            count = 1L
        )
        coEvery { api.fetchManifest(any()) } returns manifest(20260928L)
        coEvery { api.downloadDataFile(any()) } returns dataBytes

        val result = manager.refreshIfStale(force = true)

        assertEquals(WaterSyncOutcome.Updated(20260928L), result.getOrNull())
    }

    @Test
    fun `reports up to date when the manifest version is not newer`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(20260928L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } returns manifest(20260928L)

        val result = manager.refreshIfStale()

        assertEquals(WaterSyncOutcome.UpToDate, result.getOrNull())
        coVerify(exactly = 1) { repository.setLastCheckedAt(any()) }
        coVerify(exactly = 0) { repository.replaceAll(any()) }
        coVerify(exactly = 0) { api.downloadDataFile(any()) }
    }

    @Test
    fun `rejects a dataset with a mismatched sha and keeps the old data`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(20260901L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } returns manifest(20260928L, sha = "00".repeat(32))
        coEvery { api.downloadDataFile(any()) } returns dataBytes

        val result = manager.refreshIfStale()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.replaceAll(any()) }
        coVerify(exactly = 0) { repository.setDataState(any(), any(), any(), any()) }
    }

    @Test
    fun `rejects a dataset with a mismatched size and keeps the old data`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(20260901L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } returns manifest(20260928L, bytes = 999_999L)
        coEvery { api.downloadDataFile(any()) } returns dataBytes

        val result = manager.refreshIfStale()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.replaceAll(any()) }
    }

    @Test
    fun `rejects an empty downloaded dataset without clearing good data`() = runTest {
        val emptyBytes = """{"type":"FeatureCollection","features":[]}""".encodeToByteArray()
        coEvery { repository.getDataState() } returns WaterDataState(20260901L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } returns manifest(
            20260928L,
            bytes = emptyBytes.size.toLong(),
            sha = Sha256.hex(emptyBytes)
        )
        coEvery { api.downloadDataFile(any()) } returns emptyBytes

        assertTrue(manager.refreshIfStale().isFailure)
        coVerify(exactly = 0) { repository.replaceAll(any()) }
    }

    @Test
    fun `applies a verified dataset and persists the version`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(20260901L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } returns manifest(20260928L)
        coEvery { api.downloadDataFile(any()) } returns dataBytes

        val result = manager.refreshIfStale()

        assertEquals(WaterSyncOutcome.Updated(20260928L), result.getOrNull())
        coVerify(exactly = 1) { repository.replaceAll(any()) }
        coVerify(exactly = 1) { repository.setDataState(20260928L, any(), dataSha, 1L) }
    }

    @Test
    fun `network failure leaves the dataset untouched`() = runTest {
        coEvery { repository.getDataState() } returns WaterDataState(20260901L, 0L, null, 1L)
        coEvery { api.fetchManifest(any()) } throws IOException("offline")

        val result = manager.refreshIfStale()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.replaceAll(any()) }
        coVerify(exactly = 0) { repository.setDataState(any(), any(), any(), any()) }
    }
}
