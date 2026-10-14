package com.indiana.zwl.domain.usecase

import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.isTransientRemoteError
import kotlinx.coroutines.CancellationException

class GetFireRiskUseCase(private val fireApi: BdlFireApi) {

    /** Snapshot of the last persisted fire-risk code for the same scope. */
    data class Cached(val level: Int?, val timestamp: Long?)

    /**
     * UI fire-risk code for a coordinate. [now] is the platform wall clock
     * (Kotlin/Native has none). [cached] is read lazily, only on a transient
     * remote failure. [onNonTransientError] is an optional debug hook (used by
     * Android MainViewModel) — never used for control flow.
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        now: Long,
        cached: (suspend () -> Cached)? = null,
        onNonTransientError: ((Throwable) -> Unit)? = null
    ): Int {
        return try {
            val code = fireApi.getFireHazard(geometry = "$longitude,$latitude")
                .features?.firstOrNull()?.properties?.kodInt
            if (code != null && code in 0..3) code else NO_DATA
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isTransientRemoteError(e)) {
                resolveCached(cached?.invoke(), now)
            } else {
                onNonTransientError?.invoke(e)
                NO_DATA
            }
        }
    }

    companion object {
        const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val NO_CONNECTION = -1
        const val NO_DATA = -2

        fun resolveCached(cached: Cached?, now: Long): Int =
            if (cached?.level != null && cached.level in 0..3 &&
                cached.timestamp != null && now - cached.timestamp < CACHE_MAX_AGE_MS
            ) cached.level + 10 else NO_CONNECTION
    }
}
