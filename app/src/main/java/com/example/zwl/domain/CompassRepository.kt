package com.example.zwl.domain

import kotlinx.coroutines.flow.Flow

interface CompassRepository {
    val azimuthFlow: Flow<Float>
    fun startListening()
    fun stopListening()
}
