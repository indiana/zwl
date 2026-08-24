package com.indiana.zwl.presentation.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.layer.cache.TileCache

/**
 * Creates the tile cache for a single MapView instance.
 *
 * Creation must run on [Dispatchers.IO]: AndroidUtil.createTileCache resolves
 * the external cache directory through Context.getExternalCacheDir, which performs
 * synchronous binder calls into StorageManager (ensureExternalDirsExistOrFilter /
 * mkdirs). On devices with a slow or not-yet-READY external volume this can block
 * for seconds and previously caused ANRs when invoked on the main thread.
 *
 * IMPORTANT: each MapView needs its OWN TileCache. mapsforge caches assume a single
 * owner: TileLayer.draw() overwrites the cache working set on every frame and
 * TwoLevelTileCache.put() only stores into the in-memory level when the key is part
 * of that working set. Sharing one cache between the concurrently composed map views
 * makes them evict/overwrite each other's working set, which leaves freshly
 * downloaded tiles out of the memory level and shows as missing tiles.
 *
 * The parameters mirror what the map factory used to pass: tileSize 256 is the
 * DisplayModel default used by the app and OVERDRAW_FACTOR is the FrameBufferModel
 * default; the factory sets the same factor explicitly so both stay coupled.
 */
object MapTileCache {

    private const val CACHE_ID = "mapcache"
    private const val TILE_SIZE = 256
    private const val SCREEN_RATIO = 1f
    const val OVERDRAW_FACTOR = 1.2

    suspend fun create(context: Context): TileCache = withContext(Dispatchers.IO) {
        AndroidUtil.createTileCache(
            context.applicationContext,
            CACHE_ID,
            TILE_SIZE,
            SCREEN_RATIO,
            OVERDRAW_FACTOR,
            true
        )
    }
}
