using Toybox.Math;

// Port logiki przestrzennej z shared/.../domain/SpatialEngine.kt.
// Bez JTS - ray casting + haversine + initial bearing.
module Spatial {
    const EARTH_RADIUS_M = 6371000.0;

    // Odleglosc great-circle w metrach.
    function haversine(lat1, lon1, lat2, lon2) {
        var dLat = Math.toRadians(lat2 - lat1);
        var dLon = Math.toRadians(lon2 - lon1);
        var sLat1 = Math.sin(dLat / 2.0);
        var sLon1 = Math.sin(dLon / 2.0);
        var a = sLat1 * sLat1 +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sLon1 * sLon1;
        var c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_M * c;
    }

    // Azymut poczatkowy w stopniach (0 = polnoc).
    function initialBearing(lat1, lon1, lat2, lon2) {
        var lat1r = Math.toRadians(lat1);
        var lat2r = Math.toRadians(lat2);
        var dLon = Math.toRadians(lon2 - lon1);
        var y = Math.sin(dLon) * Math.cos(lat2r);
        var x = Math.cos(lat1r) * Math.sin(lat2r) -
                Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(dLon);
        var brng = Math.toDegrees(Math.atan2(y, x));
        // Normalizacja do 0..360 (Monkey C nie wspiera modulo na floatach).
        while (brng >= 360.0) { brng -= 360.0; }
        while (brng < 0.0) { brng += 360.0; }
        return brng;
    }

    // Ray casting. `ring` to tablica par [lon, lat]. Zwraca true, gdy punkt w srodku.
    function pointInRing(lat, lon, ring) {
        var inside = false;
        var n = ring.size();
        if (n < 3) { return false; }
        var j = n - 1;
        for (var i = 0; i < n; i++) {
            var xi = ring[i][0];
            var yi = ring[i][1];
            var xj = ring[j][0];
            var yj = ring[j][1];
            var crosses = ((yi > lat) != (yj > lat)) &&
                          (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (crosses) { inside = !inside; }
            j = i;
        }
        return inside;
    }
}
