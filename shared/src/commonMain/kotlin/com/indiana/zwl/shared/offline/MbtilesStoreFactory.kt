package com.indiana.zwl.shared.offline

/**
 * Creates per-area [MbtilesStore] instances backed by named MBTiles files
 * inside the platform's offline cache directory (`area_<epochMillis>.mbtiles`).
 * The factory indirection keeps the download coordinator platform-neutral:
 * Android wires it to SQLite + `java.io.File`, iOS to SQLiter.
 */
fun interface MbtilesStoreFactory {
    fun create(fileName: String): MbtilesStore
}
