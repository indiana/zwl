package com.example.zwl.presentation

import com.example.zwl.domain.model.LocationStatus

sealed class MainUiState {
    object Loading : MainUiState()
    object PermissionsRequired : MainUiState()
    object EmptyDatabaseRequired : MainUiState()
    data class Success(
        val locationStatus: LocationStatus,
        val fireRiskLevel: Int,
        val azimuth: Float,
        val latitude: Double,
        val longitude: Double
    ) : MainUiState()
}
