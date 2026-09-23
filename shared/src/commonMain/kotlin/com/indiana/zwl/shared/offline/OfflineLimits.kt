package com.indiana.zwl.shared.offline

/**
 * Shared defaults for offline map area downloads — single source of truth for
 * both platforms (Android uses them as the packager defaults, iOS reads them
 * via SKIE).
 *
 * There is no longer a hard 2000-tile limit: above [CONFIRM_TILES_THRESHOLD]
 * the UI asks the user to confirm (the operation can take a while). The much
 * higher [MAX_TILES] stays only as an anti-OOM / anti-abuse safety ceiling —
 * enumerating an unbounded region would allocate millions of [TileRef]s and
 * hammer the tile server.
 */
object OfflineLimits {
    const val MIN_ZOOM = 10
    const val MAX_ZOOM = 16

    /** Above this tile count the UI must ask the user to confirm. */
    const val CONFIRM_TILES_THRESHOLD = 2000

    /** Defensive ceiling — above this the download is refused outright. */
    const val MAX_TILES = 100_000
}
