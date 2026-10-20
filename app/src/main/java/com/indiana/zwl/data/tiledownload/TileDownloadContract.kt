package com.indiana.zwl.data.tiledownload

/**
 * Shared vocabulary of the tile download job: the unique work name, the
 * notification id and the input/progress data keys. Kept in one place because
 * the controller writes the input and the worker (plus the janitor, via
 * [TileDownloadController.protectedFileNames]) reads it back.
 */
object TileDownloadContract {
    const val WORK_NAME = "zwl_tile_download"
    const val TAG = "tile-download"
    const val NOTIFICATION_ID = 4301

    const val KEY_MODE = "mode"
    const val KEY_STARTED_AT = "started_at"
    const val KEY_AREA_ID = "area_id"
    const val KEY_LAT_SOUTH = "lat_south"
    const val KEY_LAT_NORTH = "lat_north"
    const val KEY_LON_WEST = "lon_west"
    const val KEY_LON_EAST = "lon_east"
    const val KEY_MIN_ZOOM = "min_zoom"
    const val KEY_MAX_ZOOM = "max_zoom"

    const val MODE_DOWNLOAD = "download"
    const val MODE_REFRESH = "refresh"

    /** Total runs of one job (first run + retries) before giving up. */
    const val MAX_WORK_ATTEMPTS = 3
    const val RETRY_BACKOFF_SECONDS = 30L
}
