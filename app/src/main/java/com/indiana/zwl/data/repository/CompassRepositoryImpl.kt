package com.indiana.zwl.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.indiana.zwl.domain.CompassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CompassRepositoryImpl(context: Context) : CompassRepository, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuthFlow = MutableStateFlow(0f)
    override val azimuthFlow: StateFlow<Float> = _azimuthFlow

    private var useRotationVector = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private var sinSum = 0f
    private var cosSum = 0f
    private var isInitialized = false

    override fun startListening() {
        useRotationVector = rotationVectorSensor != null
        if (useRotationVector) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun stopListening() {
        sensorManager.unregisterListener(this)
        lastAccelerometerSet = false
        lastMagnetometerSet = false
        isInitialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val rawAzimuth = orientation[0]
            val degrees = Math.toDegrees(rawAzimuth.toDouble()).toFloat()
            val normalized = (degrees + 360) % 360
            _azimuthFlow.value = normalized
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
                lastAccelerometerSet = true
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
                lastMagnetometerSet = true
            }

            if (lastAccelerometerSet && lastMagnetometerSet) {
                val success = SensorManager.getRotationMatrix(
                    rotationMatrix, null, lastAccelerometer, lastMagnetometer
                )
                if (success) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val rawAzimuth = orientation[0]

                    val currentSin = sin(rawAzimuth)
                    val currentCos = cos(rawAzimuth)

                    if (!isInitialized) {
                        sinSum = currentSin
                        cosSum = currentCos
                        isInitialized = true
                    } else {
                        val alpha = 0.8f
                        sinSum = sinSum * alpha + currentSin * (1f - alpha)
                        cosSum = cosSum * alpha + currentCos * (1f - alpha)
                    }

                    val smoothedAzimuthRad = atan2(sinSum, cosSum)
                    val degrees = Math.toDegrees(smoothedAzimuthRad.toDouble()).toFloat()
                    val normalized = (degrees + 360) % 360
                    _azimuthFlow.value = normalized
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
