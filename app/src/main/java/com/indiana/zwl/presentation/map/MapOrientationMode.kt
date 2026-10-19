package com.indiana.zwl.presentation.map

/** How the map camera is oriented relative to the real world. */
enum class MapOrientationMode {
    /** North is always up (default). */
    NORTH_UP,

    /** The device's marching direction (azimuth) is rotated to the top of the screen. */
    HEADING_UP
}

/** Shared SharedPreferences keys for map settings persisted in "zwl_map_settings". */
object MapSettingsPrefsKeys {
    const val ORIENTATION_MODE = "orientation_mode"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val LAST_SEEN_VERSION = "last_seen_version"
}
