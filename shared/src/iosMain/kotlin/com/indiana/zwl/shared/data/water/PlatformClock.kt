package com.indiana.zwl.shared.data.water

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual object PlatformClock {
    actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
