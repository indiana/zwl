package com.example.zwl.presentation

import com.example.zwl.domain.model.LocationStatus

sealed class MainUiState {
    object Loading : MainUiState()
    object PermissionsRequired : MainUiState()
    object EmptyDatabaseRequired : MainUiState()
    data class Success(
        val locationStatus: LocationStatus,
        val fireRiskLevel: Int, // 0-3, -1 oznacza brak danych (offline)
        val azimuth: Float
    ) : MainUiState()
}
