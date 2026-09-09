package com.indiana.zwl.shared.offline

/**
 * Platform file-system adapter for the per-area offline tile files
 * (`area_<epochMillis>.mbtiles` living in one dedicated directory per
 * platform: Android `externalCacheDir/mapcache`, iOS
 * `Application Support/databases`). Interface (not an expect class) so tests
 * can provide in-memory fakes.
 */
interface OfflineAreaFiles {
    /** Directory holding the area files; created on demand. */
    fun areasDirPath(): String

    /** Absolute path of a named area file (no existence check). */
    fun filePath(fileName: String): String

    /** Size in bytes, 0 when missing. */
    fun fileSize(fileName: String): Long

    /** Whether the file exists on disk. */
    fun fileExists(fileName: String): Boolean

    /** Deletes the file plus SQLite side files (-wal/-shm/-journal). */
    fun deleteFile(fileName: String): Boolean

    /** File names (not paths) currently present in the area directory. */
    fun listFileNames(): List<String>

    /** Epoch millis — wall clock is platform-specific in common code. */
    fun nowMillis(): Long
}
