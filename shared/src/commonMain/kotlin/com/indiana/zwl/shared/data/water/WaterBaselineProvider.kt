package com.indiana.zwl.shared.data.water

interface WaterBaselineProvider {
    fun load(): ByteArray?
}
