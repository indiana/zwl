package com.indiana.zwl.presentation

import android.content.Context
import android.content.SharedPreferences
import com.indiana.zwl.MainDispatcherRule
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetForestBansUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncForestBansUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import com.indiana.zwl.presentation.map.MapSettingsPrefsKeys
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowModeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneRepository: ZoneRepository = mockk()
    private val poiRepository: PoiRepository = mockk(relaxed = true)
    private val savedPointRepository: SavedPointRepository = mockk(relaxed = true)
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

    @Before
    fun setUp() {
        every { context.getSharedPreferences("zwl_map_settings", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getBoolean(match<String> { it.startsWith("show_poi_") }, true) } returns true
        every { sharedPreferences.getBoolean("show_forest_bans", true) } returns true
        every { sharedPreferences.getInt(MapSettingsPrefsKeys.ORIENTATION_MODE, 0) } returns 0
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putBoolean(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } returns sharedPreferencesEditor

        coEvery { syncPoiUseCase() } returns Result.success(Unit)
        coEvery { syncForestBansUseCase() } returns Result.success(emptyList())
        coEvery { getForestBansUseCase() } returns emptyList()
        every { getForestBansUseCase.asFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { zoneRepository.getZonesCount() } returns 5
        coEvery { getZonesUseCase() } returns emptyList<Zone>()
    }

    private fun createViewModel() = MainViewModel(
        zoneRepository, poiRepository, savedPointRepository, locationRepository, compassRepository,
        syncZonesUseCase, syncPoiUseCase, syncForestBansUseCase,
        getForestBansUseCase, getFireRiskUseCase,
        getZonesUseCase, spatialEngine, mainDispatcherRule.testDispatcher, context
    )

    @Test
    fun `follow starts enabled`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.followsUser.value)
        assertEquals(0, viewModel.recenterSignal.value)
    }

    @Test
    fun `onMapPanned disables follow`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapPanned()
        assertFalse(viewModel.followsUser.value)
    }

    @Test
    fun `recenterMap enables follow and bumps the signal`() = runTest {
        val viewModel = createViewModel()
        viewModel.onMapPanned()
        assertFalse(viewModel.followsUser.value)

        viewModel.recenterMap()
        assertTrue(viewModel.followsUser.value)
        assertEquals(1, viewModel.recenterSignal.value)

        viewModel.recenterMap()
        assertEquals(2, viewModel.recenterSignal.value)
    }

    @Test
    fun `selectSavedPoint disables follow`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.followsUser.value)

        viewModel.selectSavedPoint(SavedPoint(id = 1L, name = "Punkt", latitude = 52.0, longitude = 21.0))
        assertFalse(viewModel.followsUser.value)
    }
}
