package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.Zone
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.distance.DistanceOp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 bounding box of a zone, ready to feed the map camera. */
data class ZoneBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

/**
 * User position passed to [SearchZonesUseCase.prepare]. A wrapper class keeps
 * the Swift call site free of nullable-primitive interop (Kotlin `Double?`
 * exports as `KotlinDouble?`).
 */
data class ZoneSearchLocation(
    val lat: Double,
    val lon: Double
)

/** Result ordering of the zone search. */
enum class ZoneSortMode {
    ALPHABETICAL,
    DISTANCE
}

/**
 * A zone paired with its camera bbox and (optionally) distance to the user.
 * Geometry parsing and the distance scan are the expensive part, so this is
 * produced by [SearchZonesUseCase.prepare] only when the zone list or the
 * user location changes — not on every keystroke.
 */
data class PreparedZone(
    val zone: Zone,
    val bounds: ZoneBounds,
    val distanceMeters: Double?
)

/** Half-open `[start, end)` UTF-16 range of a query token inside the zone name. */
data class ZoneMatchSpan(val start: Int, val end: Int)

/** A single search hit ready for display. */
data class ZoneSearchResult(
    val zone: Zone,
    val bounds: ZoneBounds,
    val distanceMeters: Double?,
    val matchSpans: List<ZoneMatchSpan>
)

/**
 * Zone (nadleśnictwo) name search shared by Android and iOS.
 *
 * Two-stage API keeps the per-keystroke work trivial:
 *  - [prepare] — parses WKT geometry into bbox + distance to the user. Heavy;
 *    run on a background dispatcher whenever zones/location change.
 *  - [search] — string filter + sort over already-prepared zones. Cheap enough
 *    to run synchronously on every query change.
 *
 * Matching is case-insensitive and ignores Polish diacritics, so "spychow"
 * and "SPYCH" both find "Nadleśnictwo Spychowo". Every whitespace-separated
 * token must occur in the name.
 */
class SearchZonesUseCase {

    /**
     * Lowercases and strips Polish diacritics. The mapping is 1 character ->
     * 1 character for normal text, so character offsets in the normalized
     * string line up with the original (used for match highlighting).
     */
    fun normalize(text: String): String {
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

    /**
     * Parses every zone's WKT into a bounding box and, when [userLat]/
     * [userLon] are known, the distance from the user to the zone boundary
     * (0 m when inside). Zones whose geometry cannot be parsed are dropped.
     */
    fun prepare(zones: List<Zone>, userLat: Double?, userLon: Double?): List<PreparedZone> {
        val reader = WKTReader()
        return zones.mapNotNull { zone ->
            val geometry = try {
                reader.read(zone.geometryWkt)
            } catch (t: Throwable) {
                null
            } ?: return@mapNotNull null
            val envelope = geometry.getEnvelopeInternal()
            PreparedZone(
                zone = zone,
                bounds = ZoneBounds(
                    south = envelope.getMinY(),
                    west = envelope.getMinX(),
                    north = envelope.getMaxY(),
                    east = envelope.getMaxX()
                ),
                distanceMeters = if (userLat != null && userLon != null) {
                    distanceToGeometryMeters(geometry, userLat, userLon)
                } else {
                    null
                }
            )
        }
    }

    /** Overload for callers without a user position (Swift interop friendly). */
    fun prepare(zones: List<Zone>, location: ZoneSearchLocation?): List<PreparedZone> =
        prepare(zones, location?.lat, location?.lon)

    /** Filters [prepared] by [query] and sorts according to [sortMode]. */
    fun search(
        prepared: List<PreparedZone>,
        query: String,
        sortMode: ZoneSortMode
    ): List<ZoneSearchResult> {
        val tokens = tokenize(query)
        val results = prepared.mapNotNull { item ->
            val name = item.zone.forestDistrict
            val normalizedName = normalize(name)
            if (tokens.any { !normalizedName.contains(it) }) return@mapNotNull null
            ZoneSearchResult(
                zone = item.zone,
                bounds = item.bounds,
                distanceMeters = item.distanceMeters,
                matchSpans = if (normalizedName.length == name.length) {
                    matchSpans(normalizedName, tokens)
                } else {
                    // Pathological characters whose lowercase form changes the
                    // string length would shift the offsets — skip highlighting
                    // instead of bolding the wrong slice.
                    emptyList()
                }
            )
        }
        return when (sortMode) {
            ZoneSortMode.ALPHABETICAL -> results.sortedWith(
                compareBy({ normalize(it.zone.forestDistrict) }, { it.zone.forestDistrict })
            )
            // Without a location every distance is null and the name comparator
            // keeps the list stable (alphabetical).
            ZoneSortMode.DISTANCE -> results.sortedWith(
                compareBy<ZoneSearchResult, Double?>(nullsLast()) { it.distanceMeters }
                    .thenBy { normalize(it.zone.forestDistrict) }
            )
        }
    }

    private fun tokenize(query: String): List<String> =
        normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun matchSpans(normalizedName: String, tokens: List<String>): List<ZoneMatchSpan> {
        if (tokens.isEmpty()) return emptyList()
        val accepted = ArrayList<ZoneMatchSpan>(tokens.size)
        for (token in tokens) {
            val start = normalizedName.indexOf(token)
            if (start < 0) continue
            val end = start + token.length
            if (accepted.any { start < it.end && it.start < end }) continue
            accepted += ZoneMatchSpan(start = start, end = end)
        }
        return accepted.sortedBy { it.start }
    }

    private fun distanceToGeometryMeters(geometry: Geometry, lat: Double, lon: Double): Double? {
        return try {
            val userPoint = GeometryFactory().createPoint(Coordinate(lon, lat))
            val nearest = DistanceOp(geometry, userPoint).nearestPoints()
            val target = nearest[0]
            haversineMeters(lat, lon, target.y, target.x)
        } catch (t: Throwable) {
            null
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
