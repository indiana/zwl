package com.indiana.zwl.presentation

import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.LocationStatus

data class DebugLocationOverride(
    val locationStatus: LocationStatus,
    val forestBan: ForestBan?,
    val latitude: Double,
    val longitude: Double
)

sealed class MainUiState {
    object Loading : MainUiState()
    object PermissionsRequired : MainUiState()
    object EmptyDatabaseRequired : MainUiState()
    data class Error(val message: String) : MainUiState()
    data class Success(
        val locationStatus: LocationStatus,
        val fireRiskLevel: Int,
        val latitude: Double?,
        val longitude: Double?,
        val currentForestBan: ForestBan? = null
    ) : MainUiState()
}
