package com.indiana.zwl.shared.offline

import android.content.Context
import java.io.File

/**
 * Android adapter: area files live in `externalCacheDir/mapcache` (the same
 * directory the legacy single `map.mbtiles` used, so the janitor's legacy
 * cleanup covers old installs).
 */
class AndroidOfflineAreaFiles(private val context: Context) : OfflineAreaFiles {

    private val dir: File
        get() = File(context.externalCacheDir ?: context.cacheDir, "mapcache")

    override fun areasDirPath(): String {
        val d = dir
        if (!d.exists()) d.mkdirs()
        return d.absolutePath
    }

    override fun filePath(fileName: String): String = File(areasDirPath(), fileName).absolutePath

    override fun fileSize(fileName: String): Long =
        File(areasDirPath(), fileName).takeIf { it.isFile }?.length() ?: 0L

    override fun fileExists(fileName: String): Boolean =
        File(areasDirPath(), fileName).isFile

    override fun deleteFile(fileName: String): Boolean {
        var removedAnything = false
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val f = File(areasDirPath(), fileName + suffix)
            if (f.exists() && f.delete()) removedAnything = true
        }
        return removedAnything
    }

    override fun listFileNames(): List<String> =
        dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

    override fun nowMillis(): Long = System.currentTimeMillis()
}
