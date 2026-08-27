package com.indiana.zwl.shared.data.offline

import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.TileRef
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.sqlite3.SQLITE_DONE
import platform.sqlite3.SQLITE_OK
import platform.sqlite3.SQLITE_ROW
import platform.sqlite3.SQLITE_TRANSIENT
import platform.sqlite3.sqlite3
import platform.sqlite3.sqlite3_bind_blob
import platform.sqlite3.sqlite3_bind_int
import platform.sqlite3.sqlite3_bind_text
import platform.sqlite3.sqlite3_close
import platform.sqlite3.sqlite3_column_int
import platform.sqlite3.sqlite3_column_text
import platform.sqlite3.sqlite3_errmsg
import platform.sqlite3.sqlite3_exec
import platform.sqlite3.sqlite3_finalize
import platform.sqlite3.sqlite3_open
import platform.sqlite3.sqlite3_prepare_v2
import platform.sqlite3.sqlite3_step
import platform.sqlite3.sqlite3_stmt

/**
 * iOS adapter for [MbtilesStore] backed by the system SQLite (platform.sqlite3).
 * The file at [filePath] is created/overwritten on [open].
 */
@OptIn(ExperimentalForeignApi::class)
class IosMbtilesStore(private val filePath: String) : MbtilesStore {

    private var db: CPointer<sqlite3>? = null

    override fun open(bounds: String, minZoom: Int, maxZoom: Int) {
        close()
        memScoped {
            val handle = alloc<CPointerVar<sqlite3>>()
            val rc = sqlite3_open(filePath.cstr, handle.ptr)
            check(rc == SQLITE_OK) { "Cannot open mbtiles db: ${lastErrorMessage(handle.value)} (rc=$rc)" }
            db = handle.value
        }

        exec(
            "CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT);" +
                "CREATE TABLE IF NOT EXISTS tiles (" +
                "zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);"
        )
        exec("CREATE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row);")
        putMetadata("name", "zwl-offline")
        putMetadata("type", "baselayer")
        putMetadata("version", "1")
        putMetadata("description", "ZWL offline raster tiles")
        putMetadata("format", "png")
        putMetadata("bounds", bounds)
        putMetadata("minzoom", minZoom.toString())
        putMetadata("maxzoom", maxZoom.toString())
    }

    override fun existingTiles(): Set<TileRef> {
        val result = mutableSetOf<TileRef>()
        val stmt = prepare("SELECT zoom_level, tile_column, tile_row FROM tiles;")
        try {
            while (true) {
                when (sqlite3_step(stmt)) {
                    SQLITE_ROW -> result.add(
                        TileRef(
                            z = sqlite3_column_int(stmt, 0),
                            x = sqlite3_column_int(stmt, 1),
                            y = sqlite3_column_int(stmt, 2)
                        )
                    )
                    SQLITE_DONE -> break
                    else -> break
                }
            }
        } finally {
            sqlite3_finalize(stmt)
        }
        return result
    }

    override fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray) {
        val stmt = prepare(
            "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?);"
        )
        try {
            blob.usePinned { pinned ->
                check(sqlite3_bind_int(stmt, 1, zoom) == SQLITE_OK)
                check(sqlite3_bind_int(stmt, 2, column) == SQLITE_OK)
                check(sqlite3_bind_int(stmt, 3, tmsRow) == SQLITE_OK)
                check(sqlite3_bind_blob(stmt, 4, pinned.addressOf(0), blob.size, SQLITE_TRANSIENT) == SQLITE_OK)
                checkStep(stmt)
            }
        } finally {
            sqlite3_finalize(stmt)
        }
    }

    override fun close() {
        db?.let { sqlite3_close(it) }
        db = null
    }

    private fun putMetadata(name: String, value: String) {
        val stmt = prepare("INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?);")
        try {
            check(sqlite3_bind_text(stmt, 1, name.cstr, -1, SQLITE_TRANSIENT) == SQLITE_OK)
            check(sqlite3_bind_text(stmt, 2, value.cstr, -1, SQLITE_TRANSIENT) == SQLITE_OK)
            checkStep(stmt)
        } finally {
            sqlite3_finalize(stmt)
        }
    }

    private fun prepare(sql: String): CPointer<sqlite3_stmt>? {
        val currentDb = db ?: throw IllegalStateException("DB is not open")
        return memScoped {
            val stmt = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(currentDb, sql.cstr, -1, stmt.ptr, null)
            check(rc == SQLITE_OK) { "sqlite prepare failed: $sql (rc=$rc)" }
            stmt.value
        }
    }

    private fun checkStep(stmt: CPointer<sqlite3_stmt>?) {
        when (sqlite3_step(stmt)) {
            SQLITE_DONE -> Unit
            else -> throw IllegalStateException("sqlite step failed")
        }
    }

    private fun exec(sql: String) {
        val currentDb = db ?: throw IllegalStateException("DB is not open")
        val rc = sqlite3_exec(currentDb, sql, null, null, null)
        check(rc == SQLITE_OK) { "sqlite exec failed: $sql (rc=$rc)" }
    }

    private fun lastErrorMessage(handle: CPointer<sqlite3>?): String {
        return sqlite3_errmsg(handle)?.toKString() ?: "unknown"
    }
}