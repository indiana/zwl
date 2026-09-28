package com.indiana.zwl.shared.data.water

import android.content.Context

class AndroidWaterBaselineProvider(
    private val context: Context
) : WaterBaselineProvider {
    override fun load(): ByteArray? {
        return try {
            context.assets.open(WaterDataConfig.BASELINE_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
