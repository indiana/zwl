package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.SavedPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPointSorterTest {

    private fun point(
        id: Long,
        name: String = "P$id",
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        createdAt: Long = 0L
    ) = SavedPoint(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt
    )

    @Test
    fun dateDesc_newestFirst_unknownLast() {
        val oldest = point(1, createdAt = 1_000)
        val newest = point(2, createdAt = 3_000)
        val unknown = point(3, createdAt = 0)

        val sorted = SavedPointSorter.sort(
            listOf(oldest, newest, unknown),
            SavedPointSortMode.DATE_DESC,
            null,
            null
        )

        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun dateAsc_oldestFirst_unknownLast() {
        val oldest = point(1, createdAt = 1_000)
        val newest = point(2, createdAt = 3_000)
        val unknown = point(3, createdAt = 0)

        val sorted = SavedPointSorter.sort(
            listOf(newest, oldest, unknown),
            SavedPointSortMode.DATE_ASC,
            null,
            null
        )

        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.id })
    }

    @Test
    fun dateTie_isBrokenByNormalizedThenRawName() {
        // Same timestamp: normalized names are case/accent-insensitive, so "ą"
        // (=> "a") sorts before "B".
        val upper = point(1, name = "B", createdAt = 5_000)
        val lower = point(2, name = "ą", createdAt = 5_000)

        val sorted = SavedPointSorter.sort(
            listOf(upper, lower),
            SavedPointSortMode.DATE_DESC,
            null,
            null
        )

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun distanceAsc_nearestFirst() {
        val near = point(1, latitude = 0.0, longitude = 0.01)
        val far = point(2, latitude = 0.0, longitude = 0.02)

        val sorted = SavedPointSorter.sort(
            listOf(far, near),
            SavedPointSortMode.DISTANCE_ASC,
            0.0,
            0.0
        )

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun distanceDesc_farthestFirst() {
        val near = point(1, latitude = 0.0, longitude = 0.01)
        val far = point(2, latitude = 0.0, longitude = 0.02)

        val sorted = SavedPointSorter.sort(
            listOf(near, far),
            SavedPointSortMode.DISTANCE_DESC,
            0.0,
            0.0
        )

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun distanceWithoutLocation_fallsBackToNameOrder() {
        val zebra = point(1, name = "Zebra")
        val apple = point(2, name = "apple")

        val sorted = SavedPointSorter.sort(
            listOf(zebra, apple),
            SavedPointSortMode.DISTANCE_ASC,
            null,
            null
        )

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }
}
