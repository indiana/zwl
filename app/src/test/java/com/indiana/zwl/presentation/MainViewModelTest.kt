package com.indiana.zwl.presentation

import android.content.Context
import android.content.SharedPreferences
import com.indiana.zwl.MainDispatcherRule
import com.indiana.zwl.data.local.PoiDao
import com.indiana.zwl.data.local.PoiEntity
import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
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
import app.cash.turbine.test
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneDao: ZoneDao = mockk()
    private val poiDao: PoiDao = mockk()
    private val locationRepository: LocationRepository = mockk(relaxed = true)
    private val compassRepository: CompassRepository = mockk(relaxed = true)
    private val syncZonesUseCase: SyncZonesUseCase = mockk()
    private val syncPoiUseCase: SyncPoiUseCase = mockk()
    private val getFireRiskUseCase: GetFireRiskUseCase = mockk()
    private val getZonesUseCase: GetZonesUseCase = mockk()
    private val spatialEngine: SpatialEngine = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk()
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
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putBoolean(any(), any()) } returns sharedPreferencesEditor

        every { poiDao.getAllPois() } returns allPoisFlow
        coEvery { syncPoiUseCase() } returns Result.success(Unit)
        coEvery { zoneDao.getZonesCount() } returns 10
        coEvery { getZonesUseCase() } returns emptyList()
    }

    @Test
    fun `init should transition state to PermissionsRequired when zones exist but no location permission`() = runBlocking {
        // Arrange
        coEvery { zoneDao.getZonesCount() } returns 5
        coEvery { getZonesUseCase() } returns emptyList()

        // Act
        val viewModel = MainViewModel(
            zoneDao, poiDao, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, okHttpClient, context
        )

        // Assert
        val success = waitForState(viewModel.uiState, 2000) { it is MainUiState.PermissionsRequired }
        assertTrue("State should transition to PermissionsRequired", success)
        coVerify(exactly = 1) { syncPoiUseCase() }
        coVerify(exactly = 1) { zoneDao.getZonesCount() }
        coVerify(exactly = 1) { getZonesUseCase() }
        verify(exactly = 0) { locationRepository.startLocationUpdates() }
    }

    @Test
    fun `init should call syncZonesUseCase when database is empty`() = runBlocking {
        // Arrange
        coEvery { zoneDao.getZonesCount() } returns 0
        coEvery { syncZonesUseCase() } returns Result.success(emptyList())
        coEvery { getZonesUseCase() } returns emptyList()

        // Act
        val viewModel = MainViewModel(
            zoneDao, poiDao, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, okHttpClient, context
        )

        // Assert
        val success = waitForState(viewModel.uiState, 2000) { it is MainUiState.PermissionsRequired }
        assertTrue("State should transition to PermissionsRequired", success)
        coVerify(exactly = 1) { syncZonesUseCase() }
        coVerify(exactly = 2) { getZonesUseCase() }
    }

    @Test
    fun `pois flow should filter POIs correctly based on toggle settings`() = runBlocking {
        // Arrange
        val testPois = listOf(
            PoiEntity(id = 1, code = "S1", description = "Wiata leśna", name = "Schron Turystyczny Wiata", latitude = 52.0, longitude = 21.0),
            PoiEntity(id = 2, code = "F1", description = "Palenisko", name = "Miejsce na ognisko pod dębem", latitude = 52.1, longitude = 21.1),
            PoiEntity(id = 3, code = "O1", description = "Punkt widokowy", name = "Góra widokowa", latitude = 52.2, longitude = 21.2)
        )
        allPoisFlow.value = testPois

        val viewModel = MainViewModel(
            zoneDao, poiDao, locationRepository, compassRepository,
            syncZonesUseCase, syncPoiUseCase, getFireRiskUseCase,
            getZonesUseCase, spatialEngine, okHttpClient, context
        )

        viewModel.pois.test {
            // Początkowy stan (wszystkie 3 punkty widoczne)
            val initialList = awaitItem()
            assertEquals(3, initialList.size)

            // Wyłączenie wiat (shelters)
            viewModel.setShowShelters(false)
            val listAfterShelters = awaitItem()
            assertEquals(2, listAfterShelters.size)
            assertTrue(listAfterShelters.none { it.name.contains("Wiata", ignoreCase = true) })

            // Wyłączenie ognisk (fireplaces)
            viewModel.setShowFireplaces(false)
            val listAfterFireplaces = awaitItem()
            assertEquals(1, listAfterFireplaces.size)
            assertEquals("Góra widokowa", listAfterFireplaces.first().name)

            // Wyłączenie innych
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
