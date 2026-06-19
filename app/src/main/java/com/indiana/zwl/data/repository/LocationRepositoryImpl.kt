package com.indiana.zwl.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.sqrt

class LocationRepositoryImpl(private val context: Context) : LocationRepository, SensorEventListener {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _locationFlow = MutableSharedFlow<Location>(replay = 1)
    override val locationFlow: SharedFlow<Location> = _locationFlow

    private var currentMode = LocationMode.ACTIVE
    private var lastMotionTime = System.currentTimeMillis()

    private val accelBuffer = FloatArray(100)
    private var bufferIndex = 0
    private var isBufferFull = false

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
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lastMotionTime = System.currentTimeMillis()
    }

    override fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        isBufferFull = false
        bufferIndex = 0
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesWithMode(mode: LocationMode) {
        currentMode = mode
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val interval = if (mode == LocationMode.ACTIVE) 5000L else 30000L
        val minInterval = if (mode == LocationMode.ACTIVE) 2000L else 30000L
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

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)

            accelBuffer[bufferIndex] = magnitude
            bufferIndex++
            if (bufferIndex >= accelBuffer.size) {
                bufferIndex = 0
                isBufferFull = true
            }

            if (isBufferFull) {
                var sum = 0f
                for (valMag in accelBuffer) {
                    sum += valMag
                }
                val mean = sum / accelBuffer.size

                var varianceSum = 0f
                for (valMag in accelBuffer) {
                    val diff = valMag - mean
                    varianceSum += diff * diff
                }
                val variance = varianceSum / accelBuffer.size

                if (variance > 0.1f) {
                    lastMotionTime = System.currentTimeMillis()
                    if (currentMode == LocationMode.STATIONARY) {
                        Log.d("LocationRepository", "Ruch wykryty! Przełączanie w tryb AKTYWNY.")
                        startLocationUpdatesWithMode(LocationMode.ACTIVE)
                    }
                } else {
                    val stationaryDuration = System.currentTimeMillis() - lastMotionTime
                    if (stationaryDuration > 120000L && currentMode == LocationMode.ACTIVE) {
                        Log.d("LocationRepository", "Bezruch przez >2 minuty. Przełączanie w tryb ENERGOOSZCZĘDNY.")
                        startLocationUpdatesWithMode(LocationMode.STATIONARY)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
