package com.indiana.zwl.domain.model

data class Poi(
    val id: Long,
    val code: String,
    val description: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    /**
     * iOS/SKIE accessor for [description]: the bare `description` name collides
     * with `NSObject.description` after ObjC export, which makes Kotlin/Native
     * return the object's `toString()` instead of the actual value.
     */
    val categoryDescription: String get() = description
}
