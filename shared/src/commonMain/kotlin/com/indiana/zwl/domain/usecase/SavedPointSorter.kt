package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Location
import com.indiana.zwl.domain.model.SavedPoint

/** Ordering options for the "Zapisane punkty" list, shared by Android and iOS. */
enum class SavedPointSortMode {
    DATE_DESC,
    DATE_ASC,
    DISTANCE_ASC,
    DISTANCE_DESC
}

/** True for the two distance-based orderings (which need a user position). */
val SavedPointSortMode.isDistance: Boolean
    get() = this == SavedPointSortMode.DISTANCE_ASC || this == SavedPointSortMode.DISTANCE_DESC

/**
 * User position for distance sorting. A wrapper class keeps the Swift call site
 * free of nullable-primitive interop (Kotlin `Double?` exports as
 * `KotlinDouble?`).
 */
data class SavedPointSortLocation(
    val lat: Double,
    val lon: Double
)

/**
 * Sorts saved points by save date or distance to the user. Dates/distance are
 * ordered the same way on both platforms; points with an unknown date (`0`) or
 * without a user position sort last, with the normalized name and then id as
 * tie-breakers so the result is deterministic.
 */
object SavedPointSorter {

    fun sort(
        points: List<SavedPoint>,
        mode: SavedPointSortMode,
        userLat: Double?,
        userLon: Double?
    ): List<SavedPoint> {
        val user = if (userLat != null && userLon != null) Location(userLat, userLon) else null
        fun distance(point: SavedPoint): Double? =
            user?.let { Location(point.latitude, point.longitude).distanceTo(it) }

        val base: Comparator<SavedPoint> = when (mode) {
            SavedPointSortMode.DATE_DESC -> compareByDescending {
                it.createdAt.takeIf { t -> t > 0L } ?: Long.MIN_VALUE
            }
            SavedPointSortMode.DATE_ASC -> compareBy {
                it.createdAt.takeIf { t -> t > 0L } ?: Long.MAX_VALUE
            }
            SavedPointSortMode.DISTANCE_ASC -> compareBy {
                distance(it) ?: Double.MAX_VALUE
            }
            SavedPointSortMode.DISTANCE_DESC -> compareByDescending {
                distance(it) ?: Double.MIN_VALUE
            }
        }
        return points.sortedWith(
            base.thenBy { normalize(it.name) }.thenBy { it.id }
        )
    }

    /** Overload for callers without a user position (Swift interop friendly). */
    fun sort(
        points: List<SavedPoint>,
        mode: SavedPointSortMode,
        location: SavedPointSortLocation?
    ): List<SavedPoint> = sort(points, mode, location?.lat, location?.lon)

    /**
     * Lowercases and strips Polish diacritics so the name tie-breaker is
     * case/accent-insensitive (same mapping as [SearchZonesUseCase.normalize]).
     */
    private fun normalize(text: String): String {
        val lowered = text.lowercase()
        val out = CharArray(lowered.length)
        for (i in lowered.indices) {
            out[i] = when (val c = lowered[i]) {
                'ą' -> 'a'
                'ć' -> 'c'
                'ę' -> 'e'
                'ł' -> 'l'
                'ń' -> 'n'
                'ó' -> 'o'
                'ś' -> 's'
                'ź' -> 'z'
                'ż' -> 'z'
                else -> c
            }
        }
        return out.concatToString()
    }
}
