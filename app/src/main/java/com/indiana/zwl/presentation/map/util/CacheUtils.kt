package com.indiana.zwl.presentation.map.util

import java.io.File

fun cleanupCorruptedCacheFiles(dir: File) {
    try {
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        cleanupCorruptedCacheFiles(file)
                    } else if (file.isFile && file.length() == 0L) {
                        file.delete()
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
