package com.indiana.zwl.presentation

import android.content.Context
import android.content.SharedPreferences
import com.indiana.zwl.MainDispatcherRule
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.WaterSourceRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetForestBansUseCase
import com.indiana.zwl.domain.usecase.GetZonesUseCase
import com.indiana.zwl.domain.usecase.SyncForestBansUseCase
import com.indiana.zwl.domain.usecase.SyncPoiUseCase
import com.indiana.zwl.domain.usecase.SyncZonesUseCase
import com.indiana.zwl.presentation.map.MapSettingsPrefsKeys
import com.indiana.zwl.shared.data.water.WaterSyncManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeepScreenOnTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoneRepository: ZoneRepository = mockk()
    private val poiRepository: PoiRepository = mockk(relaxed = true)
    private val savedPointRepository: SavedPointRepository = mockk(relaxed = true)
    private val waterSourceRepository: WaterSourceRepository = mockk(relaxed = true)
    private val waterSyncManager: WaterSyncManager = mockk(relaxed = true)
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
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putBoolean(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } returns sharedPreferencesEditor

        coEvery { syncPoiUseCase() } returns Result.success(Unit)
        coEvery { syncForestBansUseCase() } returns Result.success(emptyList())
        coEvery { getForestBansUseCase() } returns emptyList()
        every { getForestBansUseCase.asFlow() } returns flowOf(emptyList())
        coEvery { zoneRepository.getZonesCount() } returns 5
        coEvery { getZonesUseCase() } returns emptyList<Zone>()
    }

    private fun createViewModel() = MainViewModel(
        zoneRepository, poiRepository, savedPointRepository, waterSourceRepository, waterSyncManager,
        locationRepository, compassRepository,
        syncZonesUseCase, syncPoiUseCase, syncForestBansUseCase,
        getForestBansUseCase, getFireRiskUseCase,
        getZonesUseCase, spatialEngine, mainDispatcherRule.testDispatcher, context
    )

    @Test
    fun `keep screen on defaults to false`() = runTest {
        every { sharedPreferences.getBoolean(MapSettingsPrefsKeys.KEEP_SCREEN_ON, false) } returns false
        val viewModel = createViewModel()
        assertFalse(viewModel.keepScreenOn.value)
    }

    @Test
    fun `keep screen on is restored from preferences`() = runTest {
        every { sharedPreferences.getBoolean(MapSettingsPrefsKeys.KEEP_SCREEN_ON, false) } returns true
        val viewModel = createViewModel()
        assertTrue(viewModel.keepScreenOn.value)
    }

    @Test
    fun `setKeepScreenOn updates state and persists`() = runTest {
        every { sharedPreferences.getBoolean(MapSettingsPrefsKeys.KEEP_SCREEN_ON, false) } returns false
        val viewModel = createViewModel()

        viewModel.setKeepScreenOn(true)
        assertTrue(viewModel.keepScreenOn.value)
        verify(exactly = 1) {
            sharedPreferencesEditor.putBoolean(MapSettingsPrefsKeys.KEEP_SCREEN_ON, true)
        }

        viewModel.setKeepScreenOn(false)
        assertFalse(viewModel.keepScreenOn.value)
        verify(exactly = 1) {
            sharedPreferencesEditor.putBoolean(MapSettingsPrefsKeys.KEEP_SCREEN_ON, false)
        }
    }
}
