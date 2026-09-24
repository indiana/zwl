// Model stref ZwL: point-in-polygon (z dziurami) + wybor najblizszej strefy.
// Zrodlem danych sa strefy pobrane z BDL (AppStore.zones, wariant 1 runtime) —
// brak wbudowanej geometrii w aplikacji.
module ZoneData {
    const STATUS_IN_ZONE = 0;
    const STATUS_OUTSIDE_ZONE = 1;
    const STATUS_EMPTY = 2;

    function activeZones() {
        return AppStore.zones;
    }

    function count() {
        var z = activeZones();
        return (z == null) ? 0 : z.size();
    }

    // Czy punkt trafia w pojedynczy poligon (pierscien zewnetrzny + dziury)?
    function pointInPolygon(lat, lon, polygon) {
        if (polygon.size() == 0) { return false; }
        if (!Spatial.pointInRing(lat, lon, polygon[0])) { return false; }
        for (var h = 1; h < polygon.size(); h++) {
            if (Spatial.pointInRing(lat, lon, polygon[h])) { return false; } // w dziurze
        }
        return true;
    }

    // Zwraca Dictionary:
    //   :status         -> STATUS_*
    //   :district       -> String lub Null
    //   :distanceMeters -> Float
    //   :bearing        -> Float
    function checkLocation(lat, lon) {
        var zones = activeZones();
        if (zones == null || zones.size() == 0) {
            return {
                :status         => STATUS_EMPTY,
                :district       => null,
                :distanceMeters => 0.0,
                :bearing        => 0.0
            };
        }

        // 1) Test point-in-polygon (strefa w ktorej jestesmy).
        for (var i = 0; i < zones.size(); i++) {
            var polygons = zones[i]["polygons"];
            for (var p = 0; p < polygons.size(); p++) {
                if (pointInPolygon(lat, lon, polygons[p])) {
                    return {
                        :status         => STATUS_IN_ZONE,
                        :district       => zones[i]["name"],
                        :distanceMeters => 0.0,
                        :bearing        => 0.0
                    };
                }
            }
        }

        // 2) Najblizsza strefa (zestaw jest maly - tylko okoliczne strefy).
        var bestDistrict = null;
        var bestDistance = null;
        var bestBearing = 0.0;
        for (var i = 0; i < zones.size(); i++) {
            var polygons = zones[i]["polygons"];
            for (var p = 0; p < polygons.size(); p++) {
                var rings = polygons[p];
                for (var r = 0; r < rings.size(); r++) {
                    var ring = rings[r];
                    for (var k = 0; k < ring.size(); k++) {
                        var lonP = ring[k][0];
                        var latP = ring[k][1];
                        var d = Spatial.haversine(lat, lon, latP, lonP);
                        if (bestDistance == null || d < bestDistance) {
                            bestDistance = d;
                            bestDistrict = zones[i]["name"];
                            bestBearing = Spatial.initialBearing(lat, lon, latP, lonP);
                        }
                    }
                }
            }
        }

        return {
            :status         => STATUS_OUTSIDE_ZONE,
            :district       => bestDistrict,
            :distanceMeters => (bestDistance == null ? 0.0 : bestDistance),
            :bearing        => bestBearing
        };
    }
}
