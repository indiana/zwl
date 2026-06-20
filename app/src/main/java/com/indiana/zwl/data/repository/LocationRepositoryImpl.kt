package com.indiana.zwl.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.indiana.zwl.domain.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class LocationRepositoryImpl(
    context: Context,
    private val motionDetector: MotionDetector
) : LocationRepository {

    companion object {
        const val ACTIVE_INTERVAL = 5000L
        const val ACTIVE_MIN_INTERVAL = 2000L
        const val STATIONARY_INTERVAL = 30000L
        const val STATIONARY_MIN_INTERVAL = 30000L
        const val STATIONARY_TIMEOUT_MS = 120000L
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationFlow = MutableSharedFlow<Location>(replay = 1)
    override val locationFlow: SharedFlow<Location> = _locationFlow

    private var currentMode = LocationMode.ACTIVE
    private var lastMotionTime = System.currentTimeMillis()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var motionJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let {
                _locationFlow.tryEmit(it)
            }
        }
    }

    private enum class LocationMode {
        ACTIVE, STATIONARY
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates() {
        startLocationUpdatesWithMode(LocationMode.ACTIVE)
        lastMotionTime = System.currentTimeMillis()

        motionJob?.cancel()
        motionJob = repositoryScope.launch {
            motionDetector.isMovingFlow.collect { isMoving ->
                if (isMoving) {
                    lastMotionTime = System.currentTimeMillis()
                    if (currentMode == LocationMode.STATIONARY) {
                        Log.d("LocationRepository", "Ruch wykryty! Przełączanie w tryb AKTYWNY.")
                        startLocationUpdatesWithMode(LocationMode.ACTIVE)
                    }
                } else {
                    val stationaryDuration = System.currentTimeMillis() - lastMotionTime
                    if (stationaryDuration > STATIONARY_TIMEOUT_MS && currentMode == LocationMode.ACTIVE) {
                        Log.d("LocationRepository", "Bezruch przez >2 minuty. Przełączanie w tryb ENERGOOSZCZĘDNY.")
                        startLocationUpdatesWithMode(LocationMode.STATIONARY)
                    }
                }
            }
        }
    }

    override fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        motionJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesWithMode(mode: LocationMode) {
        currentMode = mode
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val interval = if (mode == LocationMode.ACTIVE) ACTIVE_INTERVAL else STATIONARY_INTERVAL
        val minInterval = if (mode == LocationMode.ACTIVE) ACTIVE_MIN_INTERVAL else STATIONARY_MIN_INTERVAL
        val priority = if (mode == LocationMode.ACTIVE) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationRepository", "Brak uprawnień do lokalizacji: ${e.message}")
        }
    }
}
