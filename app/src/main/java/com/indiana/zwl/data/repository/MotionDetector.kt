package com.indiana.zwl.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.sqrt

class MotionDetector(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    companion object {
        const val VARIANCE_THRESHOLD = 0.1f
        const val BUFFER_SIZE = 100
    }

    val isMovingFlow: Flow<Boolean> = callbackFlow {
        val accelBuffer = FloatArray(BUFFER_SIZE)
        var bufferIndex = 0
        var isBufferFull = false

        val listener = object : SensorEventListener {
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

                        trySend(variance > VARIANCE_THRESHOLD)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }.conflate()
}
