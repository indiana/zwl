package com.indiana.zwl.shared.data.water

import com.indiana.zwl.domain.repository.WaterSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

sealed interface WaterSyncOutcome {
    data object Skipped : WaterSyncOutcome
    data object UpToDate : WaterSyncOutcome
    data class Updated(val version: Long) : WaterSyncOutcome
}

class WaterSyncManager(
    private val api: WaterDataApi,
    private val repository: WaterSourceRepository,
    private val baselineProvider: WaterBaselineProvider,
    private val json: Json
) {

    suspend fun ensureBaselineIfEmpty(): Result<Unit> {
        return try {
            if (repository.count() > 0L) return Result.success(Unit)
            val bytes = baselineProvider.load()
                ?: return Result.failure(IllegalStateException("Brak wbudowanego zbioru źródeł wody"))
            val sources = WaterSourceParser.parseBytes(bytes, json)
            if (sources.isEmpty()) {
                return Result.failure(IllegalStateException("Wbudowany zbiór źródeł wody jest pusty"))
            }
            repository.replaceAll(sources)
            repository.setDataState(
                version = WaterDataConfig.BASELINE_VERSION,
                lastCheckedAt = 0L,
                sha256 = null,
                count = sources.size.toLong()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshIfStale(force: Boolean = false): Result<WaterSyncOutcome> {
        return try {
            val now = PlatformClock.nowMillis()
            val state = repository.getDataState()
            val lastCheckedAt = state?.lastCheckedAt ?: 0L
            if (!force && lastCheckedAt > 0L && now - lastCheckedAt < WaterDataConfig.REFRESH_INTERVAL_MS) {
                return Result.success(WaterSyncOutcome.Skipped)
            }

            val manifest = api.fetchManifest(now / 86_400_000L)
            repository.setLastCheckedAt(now)

            val currentVersion = state?.dataVersion ?: 0L
            if (manifest.version <= currentVersion) {
                return Result.success(WaterSyncOutcome.UpToDate)
            }

            val bytes = api.downloadDataFile(manifest.file)
            if (bytes.size.toLong() != manifest.bytes) {
                return Result.failure(IllegalStateException("Rozmiar pliku danych wody niezgodny z manifestem"))
            }
            if (Sha256.hex(bytes) != manifest.sha256.lowercase()) {
                return Result.failure(IllegalStateException("Suma kontrolna pliku danych wody niezgodna z manifestem"))
            }

            val sources = WaterSourceParser.parseBytes(bytes, json)
            if (sources.isEmpty()) {
                return Result.failure(IllegalStateException("Pobrany zbiór źródeł wody jest pusty"))
            }

            repository.replaceAll(sources)
            repository.setDataState(
                version = manifest.version,
                lastCheckedAt = now,
                sha256 = manifest.sha256,
                count = sources.size.toLong()
            )
            Result.success(WaterSyncOutcome.Updated(manifest.version))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
