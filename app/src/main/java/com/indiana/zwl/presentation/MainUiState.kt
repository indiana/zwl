package com.indiana.zwl.presentation

import com.indiana.zwl.domain.model.LocationStatus

sealed class MainUiState {
    object Loading : MainUiState()
    object PermissionsRequired : MainUiState()
    object EmptyDatabaseRequired : MainUiState()
    data class Error(val message: String) : MainUiState()
    data class Success(
        val locationStatus: LocationStatus,
        val fireRiskLevel: Int,
        val latitude: Double?,
        val longitude: Double?
    ) : MainUiState()
}
