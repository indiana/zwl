@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.indiana.zwl.shared.data.offline

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.TileRef
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS adapter for [MbtilesStore] backed by SQLDelight's `NativeSqliteDriver`
 * (reuses the already-working iOS sqlite integration; no cinterop needed).
 *
 * Creates a dedicated per-area database file (e.g. `area_1735...mbtiles`)
 * whose schema is empty (the MBTiles tables are created explicitly here).
 * The set of already-packed tiles is kept in memory for the lifetime of the
 * store, matching the packer contract.
 */
class IosMbtilesStore(
    private val fileName: String = LEGACY_FILE_NAME
) : MbtilesStore {

    private var driver: SqlDriver? = null
    private val cachedTiles = mutableSetOf<TileRef>()

    override fun open(bounds: String, minZoom: Int, maxZoom: Int) {
        close()
        cachedTiles.clear()
        try {
            doOpen(bounds, minZoom, maxZoom)
        } catch (e: Exception) {
            // A half-written map.mbtiles (e.g. produced by a download that was
            // killed mid-write by the CPU watchdog / app quit) can be
            // unopenable: the very first metadata INSERT then throws inside
            // sqlite and, if left to propagate, aborts the whole app (iOS
            // SIGABRT on an unhandled coroutine exception). Recreate the file
            // from scratch instead of crashing.
            println("IosMbtilesStore: open failed (${e.message}); recreating database")
            deleteDatabaseFile()
            doOpen(bounds, minZoom, maxZoom)
        }
    }

    private fun doOpen(bounds: String, minZoom: Int, maxZoom: Int) {
        val db = NativeSqliteDriver(MbtilesSchema, fileName)
        try {
            db.execute(null, "CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT);", 0)
            db.execute(
                null,
                "CREATE TABLE IF NOT EXISTS tiles (" +
                    "zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);",
                0
            )
            db.execute(
                null,
                "CREATE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row);",
                0
            )
            putMetadata(db, "name", "zwl-offline")
            putMetadata(db, "type", "baselayer")
            putMetadata(db, "version", "1")
            putMetadata(db, "description", "ZWL offline raster tiles")
            putMetadata(db, "format", "png")
            putMetadata(db, "bounds", bounds)
            putMetadata(db, "minzoom", minZoom.toString())
            putMetadata(db, "maxzoom", maxZoom.toString())

            driver = db
        } catch (e: Exception) {
            try {
                db.close()
            } catch (inner: Exception) {
                println("IosMbtilesStore: close after failed open: ${inner.message}")
            }
            throw e
        }
    }

    private fun databaseDir(): String? {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        val appSupport = urls.firstOrNull() as? NSURL ?: return null
        return appSupport.path?.let { "$it/databases" }
    }

    /** Removes the database file plus any SQLite side files (SQLiter path). */
    private fun deleteDatabaseFile() {
        val dir = databaseDir() ?: return
        val fm = NSFileManager.defaultManager
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val path = "$dir/$fileName$suffix"
            if (fm.fileExistsAtPath(path)) {
                if (!fm.removeItemAtPath(path, null)) {
                    println("IosMbtilesStore: failed to remove $path")
                }
            }
        }
    }

    override fun existingTiles(): Set<TileRef> = cachedTiles.toSet()

    override fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray) {
        val db = driver ?: throw IllegalStateException("DB is not open")
        db.execute(
            null,
            "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?);",
            4
        ) {
            // SQLDelight binders are 0-based (SqliterStatement adds +1 before
            // calling sqlite3_bind; 1-based indices here threw
            // "column index out of range" / SQLITE_RANGE on iOS).
            bindLong(0, zoom.toLong())
            bindLong(1, column.toLong())
            bindLong(2, tmsRow.toLong())
            bindBytes(3, blob)
        }
        cachedTiles += TileRef(z = zoom, x = column, y = tmsRow)
    }

    override fun close() {
        driver?.close()
        driver = null
        cachedTiles.clear()
    }

    private fun putMetadata(db: SqlDriver, name: String, value: String) {
        db.execute(
            null,
            "INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?);",
            2
        ) {
            bindString(0, name)
            bindString(1, value)
        }
    }

    companion object {
        /** Pre multi-area scheme file name; cleaned up by the janitor. */
        const val LEGACY_FILE_NAME = "map.mbtiles"
    }
}

/** No-op schema — the MBTiles file deliberately holds no SQLDelight tables. */
private object MbtilesSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
        get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> = QueryResult.Unit
}