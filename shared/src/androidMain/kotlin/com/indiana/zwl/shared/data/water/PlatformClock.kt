package com.indiana.zwl.shared.data.water

actual object PlatformClock {
    actual fun nowMillis(): Long = System.currentTimeMillis()
}
