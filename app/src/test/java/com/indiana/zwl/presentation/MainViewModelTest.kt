package com.indiana.zwl.presentation

import android.content.Context
import android.content.SharedPreferences
import com.indiana.zwl.MainDispatcherRule
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetForestBansUseCase
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncForestBansUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.locationtech.jts.io.WKTReader
import app.cash.turbine.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneRepository: ZoneRepository = mockk()
    private val poiRepository: PoiRepository = mockk()
    private val locationRepository: LocationRepository = mockk(relaxed = true)
    private val compassRepository: CompassRepository = mockk(relaxed = true)
    private val syncZonesUseCase: SyncZonesUseCase = mockk()
    private val syncPoiUseCase: SyncPoiUseCase = mockk()
    private val syncForestBansUseCase: SyncForestBansUseCase = mockk()
    private val getForestBansUseCase: GetForestBansUseCase = mockk()
    private val getFireRiskUseCase: GetFireRiskUseCase = mockk()
    private val getZonesUseCase: GetZonesUseCase = mockk()
    private val spatialEngine: SpatialEngine = mockk(relaxed = true)
    private val context: Context = mockk()
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPreferencesEditor: SharedPreferences.Editor = mockk(relaxed = true)

    private val allPoisFlow = MutableStateFlow<List<PoiEntity>>(emptyList())

    @Before
    fun setUp() {
        every { context.getSharedPreferences("zwl_map_settings", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getBoolean("show_fireplaces", true) } returns true
        every { sharedPreferences.getBoolean("show_shelters", true) } returns true
        every { sharedPreferences.getBoolean("show_others", true) } returns true
        every { sharedPreferences.getBoolean("show_forest_bans", true) } returns true
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putBoolean(any(), any()) } returns sharedPreferencesEditor

        every { poiRepository.getAllPois() } returns allPoisFlow
        coEvery { syncPoiUseCase() } returns Result.success(Unit)
        coEvery { syncForestBansUseCase() } returns Result.success(emptyList<ForestBan>())
        coEvery { getForestBansUseCase() } returns emptyList()
        every { getForestBansUseCase.asFlow() } returns flowOf(emptyList())
        coEvery { zoneRepository.getZonesCount() } returns 10
        coEvery { getZonesUseCase() } returns emptyList()
    }

    @Test
    fun `init should transition state to PermissionsRequired when zones exist but no location permission`() = runBlocking {
        coEvery { zoneRepository.getZonesCount() } returns 5
        coEvery { getZonesUseCase() } returns emptyList()

        val viewModel = MainViewModel(
            zoneRepository, poiRepository, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, syncForestBansUseCase,
            getForestBansUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, context
        )

        val success = waitForState(viewModel.uiState, 2000) { it is MainUiState.PermissionsRequired }
        assertTrue("State should transition to PermissionsRequired", success)
        coVerify(exactly = 1) { syncPoiUseCase() }
        coVerify(exactly = 1) { zoneRepository.getZonesCount() }
        coVerify(exactly = 1) { getZonesUseCase() }
        verify(exactly = 0) { locationRepository.startLocationUpdates() }
    }

    @Test
    fun `init should call syncZonesUseCase when database is empty`() = runBlocking {
        coEvery { zoneRepository.getZonesCount() } returns 0
        coEvery { syncZonesUseCase() } returns Result.success(emptyList())
        coEvery { getZonesUseCase() } returns emptyList()

        val viewModel = MainViewModel(
            zoneRepository, poiRepository, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, syncForestBansUseCase,
            getForestBansUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, context
        )

        val success = waitForState(viewModel.uiState, 2000) { it is MainUiState.PermissionsRequired }
        assertTrue("State should transition to PermissionsRequired", success)
        coVerify(exactly = 1) { syncZonesUseCase() }
        coVerify(exactly = 1) { getZonesUseCase() }
    }

    @Test
    fun `pois flow should filter POIs correctly based on toggle settings`() = runBlocking {
        val testPois = listOf(
            PoiEntity(id = 1, code = "S1", description = "Wiata leśna", name = "Schron Turystyczny Wiata", latitude = 52.0, longitude = 21.0),
            PoiEntity(id = 2, code = "F1", description = "Palenisko", name = "Miejsce na ognisko pod dębem", latitude = 52.1, longitude = 21.1),
            PoiEntity(id = 3, code = "O1", description = "Punkt widokowy", name = "Góra widokowa", latitude = 52.2, longitude = 21.2)
        )
        allPoisFlow.value = testPois

        val viewModel = MainViewModel(
            zoneRepository, poiRepository, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, syncForestBansUseCase,
            getForestBansUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, context
        )

        viewModel.pois.test {
            val initialList = awaitItem()
            assertEquals(3, initialList.size)

            viewModel.setShowShelters(false)
            val listAfterShelters = awaitItem()
            assertEquals(2, listAfterShelters.size)
            assertTrue(listAfterShelters.none { it.name.contains("Wiata", ignoreCase = true) })

            viewModel.setShowFireplaces(false)
            val listAfterFireplaces = awaitItem()
            assertEquals(1, listAfterFireplaces.size)
            assertEquals("Góra widokowa", listAfterFireplaces.first().name)

            viewModel.setShowOthers(false)
            val listAfterOthers = awaitItem()
            assertTrue(listAfterOthers.isEmpty())
        }
    }

    private suspend fun <T> waitForState(
        stateFlow: StateFlow<T>,
        timeoutMs: Long,
        predicate: (T) -> Boolean
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (predicate(stateFlow.value)) {
                return true
            }
            kotlinx.coroutines.delay(10)
        }
        return false
    }
}
