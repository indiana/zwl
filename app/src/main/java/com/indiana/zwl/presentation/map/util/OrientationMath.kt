package com.indiana.zwl.presentation.map.util

import kotlin.math.abs

/** Angle helpers for map-orientation/rotation logic. */
object OrientationMath {

    /** Signed shortest rotation (in degrees, -180..180] needed to move from
     *  [from] to [to]; both inputs may be any real value (mod 360 handled). */
    fun shortestAngleDelta(from: Double, to: Double): Double {
        val d = (to - from) % 360.0
        var delta = d
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    /** Absolute distance between two compass bearings, in [0..180]. */
    fun bearingDifference(a: Double, b: Double): Double = abs(shortestAngleDelta(a, b))

    /** Result of go-to-target interpolation applied once step-by-step. */
    fun stepTowards(current: Double, target: Double, maxStep: Double): Double {
        val delta = shortestAngleDelta(current, target)
        return when {
            abs(delta) <= maxStep -> target
            else -> current + delta.coerceIn(-maxStep, maxStep)
        }
    }
}
