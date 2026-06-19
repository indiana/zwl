package com.example.zwl.domain

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    val locationFlow: Flow<Location>
    fun startLocationUpdates()
    fun stopLocationUpdates()
}
