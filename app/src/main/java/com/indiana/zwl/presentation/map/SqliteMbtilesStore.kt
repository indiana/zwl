package com.indiana.zwl.presentation.map

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.indiana.zwl.shared.offline.MbtilesStore
import com.indiana.zwl.shared.offline.TileRef
import java.io.File

internal class SqliteMbtilesStore(private val dbFile: File) : MbtilesStore {

    private var db: SQLiteDatabase? = null

    override fun open(bounds: String, minZoom: Int, maxZoom: Int) {
        dbFile.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db = database
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS tiles (" +
                "zoom_level INTEGER NOT NULL, " +
                "tile_column INTEGER NOT NULL, " +
                "tile_row INTEGER NOT NULL, " +
                "tile_data BLOB NOT NULL)"
        )
        database.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT NOT NULL, value TEXT NOT NULL)")

        val metadata = mapOf(
            "format" to "png",
            "name" to "Legalny Bushcraft offline",
            "type" to "basemap",
            "version" to "1.1",
            "minzoom" to minZoom.toString(),
            "maxzoom" to maxZoom.toString(),
            "bounds" to bounds
        )
        for ((key, value) in metadata) {
            database.execSQL(
                "INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)",
                arrayOf(key, value)
            )
        }
    }

    override fun existingTiles(): Set<TileRef> {
        val database = db ?: return emptySet()
        val result = LinkedHashSet<TileRef>()
        database.query("tiles", arrayOf("zoom_level", "tile_column", "tile_row"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(TileRef(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)))
            }
        }
        return result
    }

    override fun putTile(zoom: Int, column: Int, tmsRow: Int, blob: ByteArray) {
        val values = ContentValues().apply {
            put("zoom_level", zoom)
            put("tile_column", column)
            put("tile_row", tmsRow)
            put("tile_data", blob)
        }
        db?.insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun close() {
        db?.close()
        db = null
    }
}