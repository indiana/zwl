@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.indiana.zwl.shared.offline

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

/**
 * iOS adapter: area files live in `Application Support/databases` — the same
 * directory SQLiter uses for `IosMbtilesStore`, so MapLibre and the store
 * share one location (Android `externalCacheDir/mapcache` parity).
 */
class IosOfflineAreaFiles : OfflineAreaFiles {

    private fun dirPath(): String? {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        val appSupport = urls.firstOrNull() as? NSURL ?: return null
        return appSupport.path?.let { "$it/databases" }
    }

    override fun areasDirPath(): String {
        val path = dirPath() ?: return "databases"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, true, null, null)
        }
        return path
    }

    override fun filePath(fileName: String): String = "${areasDirPath()}/$fileName"

    override fun fileSize(fileName: String): Long {
        val fm = NSFileManager.defaultManager
        val path = filePath(fileName)
        if (!fm.fileExistsAtPath(path)) return 0L
        val attrs = fm.attributesOfItemAtPath(path, null) ?: return 0L
        return (attrs[NSFileSize] as? NSNumber)?.longValue ?: 0L
    }

    override fun fileExists(fileName: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(filePath(fileName))

    override fun deleteFile(fileName: String): Boolean {
        val fm = NSFileManager.defaultManager
        var removedAnything = false
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val path = filePath(fileName + suffix)
            if (fm.fileExistsAtPath(path) && fm.removeItemAtPath(path, null)) {
                removedAnything = true
            }
        }
        return removedAnything
    }

    override fun listFileNames(): List<String> {
        val fm = NSFileManager.defaultManager
        return fm.contentsOfDirectoryAtPath(areasDirPath(), null)
            ?.filterIsInstance<String>()
            ?: emptyList()
    }

    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
