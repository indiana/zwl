package com.indiana.zwl.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.usecase.PreparedZone
import com.indiana.zwl.domain.usecase.SearchZonesUseCase
import com.indiana.zwl.domain.usecase.ZoneSearchResult
import com.indiana.zwl.domain.usecase.ZoneSortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * State of the "Szukaj strefy" overlay: the query, the sort toggle and the
 * resulting hit list. The heavy geometry work ([SearchZonesUseCase.prepare])
 * only reruns when the zone list or the (already throttled) user location
 * changes; keystrokes only rerun the cheap string filter.
 */
@HiltViewModel
class ZoneSearchViewModel @Inject constructor() : ViewModel() {

    private val search = SearchZonesUseCase()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortMode = MutableStateFlow(ZoneSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ZoneSortMode> = _sortMode.asStateFlow()

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)

    /** Last location actually used for a prepare pass (100 m throttle). */
    private var preparedLocation: Pair<Double, Double>? = null

    private val prepared: StateFlow<List<PreparedZone>> =
        combine(_zones, _userLocation) { zones, location ->
            withContext(Dispatchers.Default) {
                search.prepare(zones, location?.first, location?.second)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<ZoneSearchResult>> =
        combine(prepared, _query, _sortMode) { preparedZones, query, sortMode ->
            search.search(preparedZones, query, sortMode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSortMode(mode: ZoneSortMode) {
        _sortMode.value = mode
    }

    fun setZones(zones: List<Zone>) {
        _zones.value = zones
    }

    /**
     * Feeds the user position into the distance sort. Ignored until the device
     * moved 100 m since the last accepted fix — location streams at ~1 Hz and
     * every accepted change reruns the geometry pass for all zones.
     */
    fun updateUserLocation(latitude: Double?, longitude: Double?) {
        if (latitude == null || longitude == null) {
            if (_userLocation.value != null) {
                preparedLocation = null
                _userLocation.value = null
            }
            return
        }
        val previous = preparedLocation
        if (previous != null && haversineMeters(previous.first, previous.second, latitude, longitude) < 100.0) {
            return
        }
        preparedLocation = latitude to longitude
        _userLocation.value = latitude to longitude
    }

    /** Whether the distance sort is usable (a user position is known). */
    val hasLocation: StateFlow<Boolean> = _userLocation
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
