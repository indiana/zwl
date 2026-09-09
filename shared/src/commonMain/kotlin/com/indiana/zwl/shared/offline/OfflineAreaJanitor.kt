package com.indiana.zwl.shared.offline

import com.indiana.zwl.domain.repository.OfflineAreaRepository

/**
 * One-time-per-launch housekeeping for the offline area store:
 * 1. drops the legacy single `map.mbtiles` cache (pre multi-area scheme —
 *    its contents were never registered per-area and are superseded),
 * 2. deletes orphaned `area_*.mbtiles` files with no DB row (downloads
 *    killed mid-write leave the file but no registration).
 */
class OfflineAreaJanitor(
    private val repository: OfflineAreaRepository,
    private val files: OfflineAreaFiles
) {

    suspend fun run() {
        try {
            files.deleteFile(LEGACY_FILE_NAME)
            val areas = repository.getAll()
            val known = areas.map { it.fileName }.toSet()
            // Orphaned files: download killed mid-write leaves the file but no row.
            files.listFileNames()
                .filter { it.endsWith(".mbtiles") && it !in known }
                .forEach { orphan ->
                    if (files.deleteFile(orphan)) {
                        println("OfflineAreaJanitor: removed orphan $orphan")
                    }
                }
            // Ghost rows: DB entry whose file is gone (e.g. the user removed
            // files manually outside the app) — the list would show areas
            // that can never render.
            areas.forEach { area ->
                if (!files.fileExists(area.fileName)) {
                    repository.delete(area.id)
                    println("OfflineAreaJanitor: removed ghost row ${area.fileName}")
                }
            }
        } catch (e: Exception) {
            println("OfflineAreaJanitor: failed: ${e.message}")
        }
    }

    companion object {
        const val LEGACY_FILE_NAME = "map.mbtiles"
    }
}
