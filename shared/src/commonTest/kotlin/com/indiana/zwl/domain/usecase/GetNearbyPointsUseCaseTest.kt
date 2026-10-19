package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.DrinkingWaterStatus
import com.indiana.zwl.domain.model.NearbyPointType
import com.indiana.zwl.domain.model.Poi
import com.indiana.zwl.domain.model.SavedPoint
import com.indiana.zwl.domain.model.WaterSource
import com.indiana.zwl.domain.model.WaterSourceType
import com.indiana.zwl.domain.util.PoiUiGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetNearbyPointsUseCaseTest {

    private val useCase = GetNearbyPointsUseCase()

    private val originLat = 52.0
    private val originLon = 21.0

    private fun savedPoint(id: Long, meters: Double, name: String = "P$id") =
        SavedPoint(id = id, name = name, latitude = north(meters), longitude = originLon)

    private fun poi(id: Long, code: String, meters: Double, name: String = "POI$id") =
        Poi(id = id, code = code, description = "opis", name = name, latitude = north(meters), longitude = originLon)

    private fun water(id: Long, type: WaterSourceType, meters: Double, name: String = "W$id") =
        WaterSource(
            id = id,
            osmId = "n$id",
            type = type,
            name = name,
            latitude = north(meters),
            longitude = originLon,
            source = "osm",
            drinkingWater = DrinkingWaterStatus.UNKNOWN,
            verified = false
        )

    /** Latitude ~meters north of the origin (1 deg lat = 111 195 m). */
    private fun north(meters: Double): Double = originLat + meters / 111_195.0

    @Test
    fun `excludes the viewed own point and poi by id`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 100.0), savedPoint(2, 200.0)),
            pois = listOf(
                poi(10, "PKT WIDOK", 300.0),
                poi(11, "PKT WIDOK", 400.0)
            ),
            waterSources = emptyList(),
            filter = NearbyFilter(radiusKm = 5f, excludeSavedPointId = 2, excludePoiId = 11)
        )

        assertEquals(listOf(1L, 10L), result.points.map { it.id })
        assertEquals(2, result.totalCount)
    }

    @Test
    fun `sorts by distance ascending across all three types`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 400.0)),
            pois = listOf(poi(10, "PKT WIDOK", 100.0)),
            waterSources = listOf(water(20, WaterSourceType.WELL, 250.0)),
            filter = NearbyFilter(radiusKm = 5f)
        )

        assertEquals(listOf(10L, 20L, 1L), result.points.map { it.id })
        assertEquals(
            listOf(NearbyPointType.POI, NearbyPointType.WATER_SOURCE, NearbyPointType.OWN_POINT),
            result.points.map { it.type }
        )
        val distances = result.points.map { it.distanceMeters }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `caps the list at the limit and reports the full count`() {
        val points = (1L..12L).map { savedPoint(it, 100.0 * it) }

        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = points,
            pois = emptyList(),
            waterSources = emptyList(),
            filter = NearbyFilter(radiusKm = 25f)
        )

        assertEquals(10, result.points.size)
        assertEquals(12, result.totalCount)
        assertEquals(1L, result.points.first().id)
    }

    @Test
    fun `respects the radius boundary`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 950.0), savedPoint(2, 1050.0)),
            pois = emptyList(),
            waterSources = emptyList(),
            filter = NearbyFilter(radiusKm = 1f)
        )

        assertEquals(listOf(1L), result.points.map { it.id })
    }

    @Test
    fun `uses five kilometers as the default radius`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 4500.0), savedPoint(2, 5500.0)),
            pois = emptyList(),
            waterSources = emptyList()
        )

        assertEquals(listOf(1L), result.points.map { it.id })
    }

    @Test
    fun `drops pois from groups hidden on the map`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = emptyList(),
            pois = listOf(poi(10, "PKT WIDOK", 100.0)),
            waterSources = emptyList(),
            filter = NearbyFilter(radiusKm = 5f, visiblePoiGroups = listOf(PoiUiGroup.ACCOMMODATION))
        )

        assertTrue(result.points.isEmpty())
    }

    @Test
    fun `respects the water sub-toggles`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = emptyList(),
            pois = emptyList(),
            waterSources = listOf(
                water(20, WaterSourceType.SPRING, 100.0),
                water(21, WaterSourceType.WELL, 200.0)
            ),
            filter = NearbyFilter(radiusKm = 5f, includeSprings = true, includeWells = false)
        )

        assertEquals(listOf(20L), result.points.map { it.id })
    }

    @Test
    fun `drops own points when the layer is hidden`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 100.0)),
            pois = listOf(poi(10, "PARKING", 200.0)),
            waterSources = emptyList(),
            filter = NearbyFilter(radiusKm = 5f, includeOwnPoints = false)
        )

        assertEquals(listOf(10L), result.points.map { it.id })
    }

    @Test
    fun `falls back to labels for blank names`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = emptyList(),
            pois = listOf(poi(10, "PKT WIDOK", 100.0, name = " ")),
            waterSources = listOf(water(20, WaterSourceType.WELL, 200.0, name = "")),
            filter = NearbyFilter(radiusKm = 5f)
        )

        assertEquals("Punkty widokowe i rekreacja", result.points[0].name)
        assertEquals("Źródło wody", result.points[1].name)
        assertEquals("Studnia", result.points[1].subtitle)
    }

    @Test
    fun `subtitle carries the poi group and water type labels`() {
        val result = useCase(
            latitude = originLat,
            longitude = originLon,
            savedPoints = listOf(savedPoint(1, 100.0)),
            pois = listOf(poi(10, "SCHRONISKO", 200.0)),
            waterSources = listOf(water(20, WaterSourceType.DRINKING_WATER, 300.0)),
            filter = NearbyFilter(radiusKm = 5f)
        )

        assertEquals("", result.points.first { it.type == NearbyPointType.OWN_POINT }.subtitle)
        assertEquals(
            "Noclegi i biwakowanie",
            result.points.first { it.type == NearbyPointType.POI }.subtitle
        )
        assertEquals(
            "Woda pitna",
            result.points.first { it.type == NearbyPointType.WATER_SOURCE }.subtitle
        )
    }
}
