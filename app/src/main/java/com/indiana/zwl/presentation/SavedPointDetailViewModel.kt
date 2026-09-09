package com.indiana.zwl.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.usecase.GetFireRiskUseCase
import com.indiana.zwl.domain.usecase.GetForestStandForPointUseCase
import com.indiana.zwl.domain.usecase.GetSoilCoverForPointUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Zone-detail style data for a saved point's properties card (fire risk +
 * stove rules + BDL forest stand with habitat/management parameters).
 * Logic mirrors [ZoneDetailViewModel.selectZone] minus the polygon distance.
 */
data class SelectedSavedPointDetails(
    val pointId: Long,
    val fireRiskLevel: Int,
    val isLoadingFireRisk: Boolean,
    val forestStand: ForestStandSummary? = null,
    val isLoadingForestStand: Boolean = false
)
@HiltViewModel
class SavedPointDetailViewModel @Inject constructor(
    private val getFireRiskUseCase: GetFireRiskUseCase,
    private val getForestStandForPointUseCase: GetForestStandForPointUseCase,
    private val getSoilCoverForPointUseCase: GetSoilCoverForPointUseCase,
    private val savedPointRepository: SavedPointRepository
) : ViewModel() {

    private val _details = MutableStateFlow<SelectedSavedPointDetails?>(null)
    val details: StateFlow<SelectedSavedPointDetails?> = _details

    fun clear() {
        _details.value = null
    }

    fun select(point: SavedPoint) {
        _details.value = SelectedSavedPointDetails(
            pointId = point.id,
            fireRiskLevel = -1,
            isLoadingFireRisk = true,
            forestStand = null,
            isLoadingForestStand = true
        )
        viewModelScope.launch {
            try {
                loadCachedForestStandIntoState(point)

                val fireRiskResult = getFireRiskUseCase(point.latitude, point.longitude)
                val riskCode = if (fireRiskResult.isSuccess) {
                    val code = fireRiskResult.getOrDefault(-1)
                    if (code in 0..3) {
                        val timestamp = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            savedPointRepository.updateFireRisk(point.id, code, timestamp)
                        }
                    }
                    code
                } else {
                    val exception = fireRiskResult.exceptionOrNull()
                    if (isNetworkException(exception)) {
                        val fresh = withContext(Dispatchers.IO) { freshPoint(point.id) }
                        resolveCachedFireRisk(fresh?.fireRiskLevel, fresh?.fireRiskTimestamp)
                    } else {
                        -1
                    }
                }

                if (_details.value?.pointId == point.id) {
                    _details.value = _details.value?.copy(
                        fireRiskLevel = riskCode,
                        isLoadingFireRisk = false
                    )
                }

                if (needsForestStandRefresh(point)) {
                    val forestStandResult = getForestStandForPointUseCase(point.latitude, point.longitude)
                    if (_details.value?.pointId == point.id) {
                        if (forestStandResult.isSuccess) {
                            val summary = forestStandResult.getOrNull()
                            if (summary != null) {
                                val json = Json.encodeToString(summary)
                                val timestamp = System.currentTimeMillis()
                                withContext(Dispatchers.IO) {
                                    savedPointRepository.updateForestStand(point.id, json, timestamp)
                                }
                            }
                            _details.value = _details.value?.copy(
                                forestStand = summary,
                                isLoadingForestStand = false
                            )
                        } else {
                            _details.value = _details.value?.copy(
                                isLoadingForestStand = false
                            )
                        }
                    }
                } else {
                    if (_details.value?.pointId == point.id) {
                        _details.value = _details.value?.copy(isLoadingForestStand = false)
                    }
                    // Cached stand is fresh — still fetch fresh SILP soil/cover
                    // and merge display-only (cache TTL untouched; the refresh
                    // branch above gets soil already merged in the summary).
                    val soilCover = try {
                        getSoilCoverForPointUseCase(point.latitude, point.longitude)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        null
                    }
                    if (soilCover != null && _details.value?.pointId == point.id) {
                        val merged = _details.value?.forestStand?.copy(
                            soilType = soilCover.soilType,
                            groundCover = soilCover.groundCover
                        )
                        _details.value = _details.value?.copy(forestStand = merged)
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadCachedForestStandIntoState(point: SavedPoint) {
        val fresh = freshPoint(point.id) ?: point
        val cached = loadCachedForestStand(fresh)
        if (_details.value?.pointId == point.id) {
            _details.value = _details.value?.copy(
                forestStand = cached,
                isLoadingForestStand = needsForestStandRefresh(fresh)
            )
        }
    }

    private suspend fun freshPoint(id: Long): SavedPoint? =
        withContext(Dispatchers.IO) {
            savedPointRepository.getAllPoints().first().firstOrNull { it.id == id }
        }

    private fun needsForestStandRefresh(point: SavedPoint): Boolean {
        val cached = loadCachedForestStand(point)
        return cached == null || isForestStandCacheStale(point)
    }

    private fun loadCachedForestStand(point: SavedPoint): ForestStandSummary? {
        val json = point.forestStandJson ?: return null
        return try {
            Json.decodeFromString<ForestStandSummary>(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isForestStandCacheStale(point: SavedPoint): Boolean {
        val timestamp = point.forestStandTimestamp ?: return true
        return System.currentTimeMillis() - timestamp > FOREST_STAND_CACHE_MAX_AGE_MS
    }

    private fun resolveCachedFireRisk(level: Int?, timestamp: Long?): Int {
        val now = System.currentTimeMillis()
        return if (level != null && level in 0..3 &&
            timestamp != null && now - timestamp < FIRE_RISK_CACHE_MAX_AGE_MS
        ) {
            level + 10
        } else {
            -2
        }
    }

    private fun isNetworkException(e: Throwable?): Boolean {
        return e is java.net.UnknownHostException ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException ||
               e is java.net.SocketException ||
               e is javax.net.ssl.SSLException
    }

    companion object {
        private const val FIRE_RISK_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val FOREST_STAND_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
