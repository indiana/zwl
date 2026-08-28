package com.indiana.zwl.shared.map

object MapStyle {
    val OSM_STYLE_JSON = """
    {
        "version": 8,
        "name": "OSM",
        "sources": {
            "osm": {
                "type": "raster",
                "tiles": [
                    "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                    "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
                    "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"
                ],
                "tileSize": 256,
                "attribution": "© OpenStreetMap contributors",
                "maxzoom": 19
            }
        },
        "layers": [
            {
                "id": "osm",
                "type": "raster",
                "source": "osm",
                "minzoom": 0,
                "maxzoom": 19
            }
        ]
    }
    """.trimIndent()

    const val DEFAULT_LAT = 52.23
    const val DEFAULT_LNG = 21.01
    const val DEFAULT_ZOOM = 15.0

    const val MIN_ZOOM = 4.0
    const val MAX_ZOOM = 20.0
}