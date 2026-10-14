package com.indiana.zwl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.LoadPointDetailsUseCase
import com.indiana.zwl.domain.usecase.PointDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingPointDetailsState(
    val fireRiskLevel: Int = GetFireRiskUseCase.NO_CONNECTION,
    val isLoadingFireRisk: Boolean = true,
    val forestStand: ForestStandSummary? = null,
    val isLoadingForestStand: Boolean = true
)

@HiltViewModel
class PendingPointDetailViewModel @Inject constructor(
    private val loadPointDetails: LoadPointDetailsUseCase
) : ViewModel() {
    private val _details = MutableStateFlow<PendingPointDetailsState?>(null)
    val details: StateFlow<PendingPointDetailsState?> = _details

    private var loadJob: Job? = null
    private var requestId = 0
    private var latest: PointDetails? = null

    fun load(point: PendingPoint) {
        loadJob?.cancel()
        requestId++
        val myId = requestId
        latest = null
        _details.value = PendingPointDetailsState()
        loadJob = viewModelScope.launch {
            val result = loadPointDetails(point.lat, point.lng, System.currentTimeMillis())
            if (requestId != myId) return@launch
            latest = result
            _details.value = PendingPointDetailsState(
                fireRiskLevel = result.fireRiskLevel,
                isLoadingFireRisk = false,
                forestStand = result.forestStand,
                isLoadingForestStand = false
            )
        }
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        requestId++
        latest = null
        _details.value = null
    }

    /** Last completed fetch (null while loading / after clear) — used to seed on save. */
    fun latestDetails(): PointDetails? = latest
}
