package com.indiana.zwl.shared.offline

/**
 * Shared defaults for offline map area downloads — single source of truth for
 * both platforms (Android uses them as the packager defaults, iOS reads them
 * via SKIE). A 2000-tile budget at zoom 10-16 covers roughly a 14x15 km
 * window in Poland (~30-100 MB per area).
 */
object OfflineLimits {
    const val MIN_ZOOM = 10
    const val MAX_ZOOM = 16
    const val MAX_TILES = 2000
}
