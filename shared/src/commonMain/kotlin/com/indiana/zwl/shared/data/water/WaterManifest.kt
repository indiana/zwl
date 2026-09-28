package com.indiana.zwl.shared.data.water

import kotlinx.serialization.Serializable

@Serializable
data class WaterManifest(
    val version: Long,
    val generatedAt: String,
    val file: String,
    val sha256: String,
    val bytes: Long,
    val count: Long,
    val sources: List<String> = emptyList()
)
