@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.indiana.zwl.shared.data.water

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

class IosWaterBaselineProvider : WaterBaselineProvider {
    override fun load(): ByteArray? {
        val path = NSBundle.mainBundle.pathForResource("water-baseline", "geojson") ?: return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray()
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
