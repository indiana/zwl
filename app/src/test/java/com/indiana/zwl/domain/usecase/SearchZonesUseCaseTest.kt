package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchZonesUseCaseTest {

    private val useCase = SearchZonesUseCase()

    private val wktSpychowo = "POLYGON ((21.30 53.60, 21.35 53.60, 21.35 53.65, 21.30 53.65, 21.30 53.60))"
    private val wktDzialdowo = "POLYGON ((20.30 53.20, 20.40 53.20, 20.40 53.30, 20.30 53.30, 20.30 53.20))"

    private val spychowo = Zone(id = 1, forestDistrict = "Nadleśnictwo Spychowo", geometryWkt = wktSpychowo)
    private val dzialdowo = Zone(id = 2, forestDistrict = "Nadleśnictwo Działdowo", geometryWkt = wktDzialdowo)

    // --- normalize ---

    @Test
    fun normalizeStripsPolishDiacriticsAndLowercases() {
        assertEquals("nadlesnictwo spychowo", useCase.normalize("Nadleśnictwo Spychowo"))
        assertEquals("dzialdowo c e l n o s z z", useCase.normalize("Działdowo Ć ę Ł Ń Ó Ś Ź Ż"))
        assertEquals("spychowo", useCase.normalize("SPYCHOWO"))
        assertEquals("already plain text", useCase.normalize("Already plain text"))
    }

    @Test
    fun normalizePreservesLengthForPolishText() {
        val name = "Nadleśnictwo Działdowo"
        assertEquals(name.length, useCase.normalize(name).length)
    }

    // --- search: matching ---

    @Test
    fun matchesIgnoringCaseAndDiacritics() {
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), null, null)

        val results = useCase.search(prepared, "SPYCH", ZoneSortMode.ALPHABETICAL)
        assertEquals(1, results.size)
        assertEquals("Nadleśnictwo Spychowo", results[0].zone.forestDistrict)

        val diacriticQuery = useCase.search(prepared, "działd", ZoneSortMode.ALPHABETICAL)
        assertEquals(1, diacriticQuery.size)
        assertEquals("Nadleśnictwo Działdowo", diacriticQuery[0].zone.forestDistrict)

        val typoTolerant = useCase.search(prepared, "dziald", ZoneSortMode.ALPHABETICAL)
        assertEquals(1, typoTolerant.size)
    }

    @Test
    fun everyTokenMustMatch() {
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), null, null)

        assertEquals(2, useCase.search(prepared, "nadleśnictwo", ZoneSortMode.ALPHABETICAL).size)
        assertEquals(1, useCase.search(prepared, "nadleśnictwo spych", ZoneSortMode.ALPHABETICAL).size)
        assertEquals(0, useCase.search(prepared, "spych działd", ZoneSortMode.ALPHABETICAL).size)
    }

    @Test
    fun blankQueryReturnsAllZones() {
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), null, null)
        assertEquals(2, useCase.search(prepared, "", ZoneSortMode.ALPHABETICAL).size)
        assertEquals(2, useCase.search(prepared, "   ", ZoneSortMode.ALPHABETICAL).size)
    }

    @Test
    fun matchSpansPointAtTheQuerySlice() {
        val prepared = useCase.prepare(listOf(spychowo), null, null)
        val results = useCase.search(prepared, "spych", ZoneSortMode.ALPHABETICAL)

        assertEquals(1, results.size)
        val spans = results[0].matchSpans
        assertEquals(1, spans.size)
        val name = results[0].zone.forestDistrict
        assertEquals("Spych", name.substring(spans[0].start, spans[0].end))
    }

    @Test
    fun matchSpansCoverEveryToken() {
        val prepared = useCase.prepare(listOf(spychowo), null, null)
        val results = useCase.search(prepared, "nadleśnictwo spych", ZoneSortMode.ALPHABETICAL)

        val name = results[0].zone.forestDistrict
        val slices = results[0].matchSpans.map { name.substring(it.start, it.end) }
        assertTrue(slices.contains("Nadleśnictwo"))
        assertTrue(slices.contains("Spych"))
    }

    // --- search: sorting ---

    @Test
    fun sortsAlphabeticallyIgnoringDiacritics() {
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), null, null)
        val results = useCase.search(prepared, "", ZoneSortMode.ALPHABETICAL)

        // "Działdowo" -> "dzialdowo" sorts before "Spychowo" -> "spychowo"
        assertEquals("Nadleśnictwo Działdowo", results[0].zone.forestDistrict)
        assertEquals("Nadleśnictwo Spychowo", results[1].zone.forestDistrict)
    }

    @Test
    fun sortsByDistanceAndKeepsNullsLastWithoutLocation() {
        val preparedNoLocation = useCase.prepare(listOf(spychowo, dzialdowo), null, null)
        for (item in preparedNoLocation) assertNull(item.distanceMeters)

        // User next to Działdowo (west of the polygon) — Działdowo must win.
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), 53.25, 20.28)
        val results = useCase.search(prepared, "", ZoneSortMode.DISTANCE)
        assertEquals("Nadleśnictwo Działdowo", results[0].zone.forestDistrict)
        assertTrue(results[0].distanceMeters!! < results[1].distanceMeters!!)
    }

    @Test
    fun distanceIsNullWhenLocationMissingEvenInDistanceSort() {
        val prepared = useCase.prepare(listOf(spychowo), null, null)
        val results = useCase.search(prepared, "", ZoneSortMode.DISTANCE)
        assertNull(results[0].distanceMeters)
    }

    // --- prepare: geometry ---

    @Test
    fun prepareComputesBoundingBoxFromWkt() {
        val prepared = useCase.prepare(listOf(spychowo), null, null)
        assertEquals(1, prepared.size)
        val bounds = prepared[0].bounds
        assertEquals(53.60, bounds.south, 1e-9)
        assertEquals(53.65, bounds.north, 1e-9)
        assertEquals(21.30, bounds.west, 1e-9)
        assertEquals(21.35, bounds.east, 1e-9)
    }

    @Test
    fun prepareDropsZonesWithBrokenGeometry() {
        val broken = Zone(id = 3, forestDistrict = "Nadleśnictwo Uszkodzone", geometryWkt = "NOT A WKT")
        val prepared = useCase.prepare(listOf(spychowo, broken), null, null)
        assertEquals(1, prepared.size)
        assertEquals(1L, prepared[0].zone.id)
    }

    @Test
    fun distanceInsideZoneIsNearZero() {
        val prepared = useCase.prepare(listOf(spychowo), 53.62, 21.32)
        assertTrue("W środku strefy dystans powinien być ~0, jest ${prepared[0].distanceMeters}",
            prepared[0].distanceMeters!! < 1.0)
    }

    @Test
    fun distanceOutsideZoneMatchesHaversineToBoundary() {
        // Same fixture as SpatialEngineTest: 0.02° west of the west edge.
        val prepared = useCase.prepare(listOf(spychowo), 53.62, 21.28)
        val distance = prepared[0].distanceMeters!!
        assertTrue("Oczekiwano ~1320 m, jest $distance", distance in 1280.0..1360.0)
    }

    @Test
    fun distancesAreOrderedFromUserPosition() {
        val prepared = useCase.prepare(listOf(spychowo, dzialdowo), 53.62, 21.30)
        val results = useCase.search(prepared, "", ZoneSortMode.DISTANCE)
        assertEquals("Nadleśnictwo Spychowo", results[0].zone.forestDistrict)
        assertTrue(results[0].distanceMeters!! < results[1].distanceMeters!!)
    }
}
