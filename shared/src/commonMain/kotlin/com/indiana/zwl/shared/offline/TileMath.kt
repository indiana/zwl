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
}

private fun ln(x: Double): Double = kotlin.math.ln(x)