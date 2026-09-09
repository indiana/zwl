package com.indiana.zwl.shared.offline

object TileMath {
    fun getTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    fun getTileY(lat: Double, zoom: Int): Int {
        val latRad = lat * kotlin.math.PI / 180.0
        val latRadBounded = maxOf(-1.484, minOf(1.484, latRad))
        val y = (1.0 - ln(kotlin.math.tan(latRadBounded) + 1.0 / kotlin.math.cos(latRadBounded)) / kotlin.math.PI) / 2.0 * (1 shl zoom)
        return y.toInt()
    }

    fun tileUrl(x: Int, y: Int, z: Int): String {
        val hosts = arrayOf("a.tile.openstreetmap.org", "b.tile.openstreetmap.org", "c.tile.openstreetmap.org")
        val host = hosts[((x xor y) and Int.MAX_VALUE) % hosts.size]
        return "https://$host/$z/$x/$y.png"
    }

    /**
     * Number of tiles the region spans across [minZoom]..[maxZoom] — the same
     * math the packager uses to enumerate, exposed so the UI can reject an
     * oversized view with a clear message before downloading starts.
     */
    fun estimateTileCount(
        region: Region,
        minZoom: Int = OfflineLimits.MIN_ZOOM,
        maxZoom: Int = OfflineLimits.MAX_ZOOM
    ): Int {
        var total = 0
        for (z in minZoom..maxZoom) {
            val x1 = getTileX(region.lonWest, z)
            val x2 = getTileX(region.lonEast, z)
            val y1 = getTileY(region.latNorth, z)
            val y2 = getTileY(region.latSouth, z)
            total += (maxOf(x1, x2) - minOf(x1, x2) + 1) * (maxOf(y1, y2) - minOf(y1, y2) + 1)
        }
        return total
    }
}

private fun ln(x: Double): Double = kotlin.math.ln(x)