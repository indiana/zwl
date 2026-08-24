package com.indiana.zwl.presentation.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.layer.cache.TileCache

/**
 * Single process-lifetime tile cache shared by every MapView instance.
 *
 * Cache creation must run on [Dispatchers.IO]: AndroidUtil.createTileCache resolves
 * the external cache directory through Context.getExternalCacheDir, which performs
 * synchronous binder calls into StorageManager (ensureExternalDirsExistOrFilter /
 * mkdirs). On devices with a slow or not-yet-READY external volume this can block
 * for seconds and previously caused ANRs when invoked on the main thread.
 *
 * The parameters mirror what the map factory used to pass: tileSize 256 is the
 * DisplayModel default used by the app and OVERDRAW_FACTOR is the FrameBufferModel
 * default; the factory sets the same factor explicitly so both stay coupled.
 */
object MapTileCacheProvider {

    private const val CACHE_ID = "mapcache"
    private const val TILE_SIZE = 256
    private const val SCREEN_RATIO = 1f
    const val OVERDRAW_FACTOR = 1.2

    @Volatile
    private var cached: TileCache? = null
    private val mutex = Mutex()

    suspend fun getOrCreate(context: Context): TileCache = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            AndroidUtil.createTileCache(
                context.applicationContext,
                CACHE_ID,
                TILE_SIZE,
                SCREEN_RATIO,
                OVERDRAW_FACTOR,
                true
            )
        }.also { cached = it }
    }
}
