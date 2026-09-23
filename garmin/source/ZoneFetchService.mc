using Toybox.Application.Storage;
using Toybox.Communications;
import Toybox.Lang;
using Toybox.Math;
using Toybox.System;
using Toybox.WatchUi;

// Pobieranie stref ZwL "w locie" z BDL (wariant 1):
//  - zapytanie ArcGIS o envelope wokol pozycji (kilka-kilkanascie stref),
//  - odpowiedz ArcGIS JSON (application/json) automatycznie parsowana przez
//    responseType=JSON; outSR=4326 => wspolrzedne lon/lat,
//  - wynik trzymamy w RAM (AppStore.zones) i cache'ujemy w Storage,
//    dzielac na chunki <= 8 KB (limit klucza Storage).
//
// Offline: jesli mamy cache, uzywamy go; inaczej brak danych (komunikat).
// Singleton: `Services.zones`.
class ZoneFetchService {
    const URL = "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WFS_BDL_mapa_turystyczna/MapServer/76/query";
    const KEY_CENTER = "zwl_center_v1";
    const KEY_COUNT = "zwl_ccount_v1";
    const KEY_CHUNK_PREFIX = "zwl_c";
    const SPAN = 0.1;        // polowa boku envelope w stopniach (~11 km)
    const REUSE = 0.06;      // jak blisko centrum, by uznac dane za aktualne
    const MAX_STORE = 7800;  // < 8 KB na klucz Storage
    const OFFSET = 0.001;    // uproszczenie geometrii po stronie serwera
    const SCALE = 10000.0;   // kwantyzacja wspolrzednych (~11 m)

    var centerLat = null;
    var centerLon = null;
    var pendingLat = null;
    var pendingLon = null;
    var fetching = false;
    var lastAttempt = 0;     // System.getTimer() ostatniej proby pobrania
    const RETRY_MS = 10000;  // minimalny odstep miedzy probami

    function initialize() {
        loadFromStorage();
    }

    // Wywolywane przy kazdej aktualizacji pozycji.
    function ensureForPosition(lat, lon) {
        if (AppStore.zones != null && nearCenter(lat, lon)) {
            return;
        }
        if (fetching) { return; }
        if ((System.getTimer() - lastAttempt) < RETRY_MS) { return; }
        fetch(lat, lon);
    }

    function nearCenter(lat, lon) {
        if (centerLat == null) { return false; }
        return (abs(lat - centerLat) <= REUSE) && (abs(lon - centerLon) <= REUSE);
    }

    function abs(v) {
        return (v < 0) ? -v : v;
    }

    function fetch(lat, lon) {
        fetching = true;
        lastAttempt = System.getTimer();
        pendingLat = lat;
        pendingLon = lon;
        AppStore.zoneState = 1;
        WatchUi.requestUpdate();

        var env = (lon - SPAN).toString() + "," + (lat - SPAN).toString() + "," +
                  (lon + SPAN).toString() + "," + (lat + SPAN).toString();
        var params = {
            "where"              => "1=1",
            "outFields"          => "link,nzw_ob",
            "geometry"           => env,
            "geometryType"       => "esriGeometryEnvelope",
            "inSR"               => 4326,
            "outSR"              => 4326,
            "spatialRel"         => "esriSpatialRelIntersects",
            "maxAllowableOffset" => OFFSET,
            "f"                  => "json"
        };
        Communications.makeWebRequest(URL, params, {
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
        }, method(:onResponse));
    }

    function onResponse(responseCode as Number, data as Dictionary or String or Null) as Void {
        fetching = false;
        if (responseCode != 200 || data == null || !(data instanceof Dictionary)) {
            AppStore.zoneState = 2;
            WatchUi.requestUpdate();
            return;
        }

        var zones = [];
        var features = data["features"];
        if (features != null) {
            for (var i = 0; i < features.size(); i++) {
                var z = parseFeature(features[i]);
                if (z != null) {
                    zones.add(z);
                }
            }
        }

        AppStore.zones = zones;
        centerLat = pendingLat;
        centerLon = pendingLon;
        AppStore.zoneState = 0;
        saveToStorage(zones);
        WatchUi.requestUpdate();
    }

    // ArcGIS JSON: { "attributes": {...}, "geometry": { "rings": [ [ [lon,lat], ... ] ] } }
    // Pierscienie traktujemy jako osobne poligony (bez wykrywania dziur orientacja).
    function parseFeature(f) {
        var geom = f["geometry"];
        if (geom == null) { return null; }
        var rings = geom["rings"];
        if (rings == null) { return null; }

        var polys = [];
        for (var i = 0; i < rings.size(); i++) {
            polys.add([ rings[i] ]);
        }

        var name = "";
        var attrs = f["attributes"];
        if (attrs != null) {
            var link = attrs["link"] as String or Null;
            if (link != null) {
                name = TextUtil.districtFromLink(link);
            }
            if (name.length() == 0) {
                var nzw = attrs["nzw_ob"] as String or Null;
                if (nzw != null && !nzw.equals("Zanocuj w lesie")) {
                    name = nzw;
                }
            }
        }
        if (name.length() == 0) {
            name = "Nieznane";
        }
        return { "name" => name, "polygons" => polys };
    }

    // ------------------------------------------------------------ Storage cache

    function saveToStorage(zones) {
        // Zapakuj cale strefy w chunki <= MAX_STORE (nie tniemy strefy w polowie).
        var chunks = [];
        var cur = "";
        for (var i = 0; i < zones.size(); i++) {
            var zs = encodeZone(zones[i]);
            if (zs.length() > MAX_STORE) { continue; } // pojedyncza strefa za duza
            if (cur.length() == 0) {
                cur = zs;
            } else if (cur.length() + 1 + zs.length() <= MAX_STORE) {
                cur = cur + "\n" + zs;
            } else {
                chunks.add(cur);
                cur = zs;
            }
        }
        if (cur.length() > 0) { chunks.add(cur); }

        // Usun stare chunki.
        var old = Storage.getValue(KEY_COUNT);
        if (old != null && old instanceof Number) {
            for (var i = 0; i < old; i++) {
                Storage.deleteValue(KEY_CHUNK_PREFIX + i);
            }
        }
        for (var i = 0; i < chunks.size(); i++) {
            Storage.setValue(KEY_CHUNK_PREFIX + i, chunks[i]);
        }
        Storage.setValue(KEY_COUNT, chunks.size());
        Storage.setValue(KEY_CENTER, centerLat.toString() + "," + centerLon.toString());
    }

    function loadFromStorage() {
        var count = Storage.getValue(KEY_COUNT);
        if (count == null || !(count instanceof Number) || count <= 0) { return; }

        var parts = [];
        for (var i = 0; i < count; i++) {
            var c = Storage.getValue(KEY_CHUNK_PREFIX + i);
            if (c != null && c instanceof String) {
                parts.add(c);
            }
        }
        if (parts.size() == 0) { return; }

        var zones = decodeZones(TextUtil.join(parts, "\n"));
        if (zones.size() == 0) { return; }
        AppStore.zones = zones;

        var center = Storage.getValue(KEY_CENTER);
        if (center != null && center instanceof String) {
            var cp = TextUtil.split(center, ",");
            if (cp.size() == 2) {
                centerLat = cp[0].toDouble();
                centerLon = cp[1].toDouble();
            }
        }
    }

    // Format: strefy "\n"; strefa = name "|" polygon...; polygon = ringi "\t";
    // ring = punkty ";" ; punkt = lonInt,latInt (skala SCALE).
    function encodeZone(z) {
        var fields = [ z["name"] ];
        var polys = z["polygons"];
        for (var p = 0; p < polys.size(); p++) {
            var rings = polys[p];
            var ringStrs = [];
            for (var r = 0; r < rings.size(); r++) {
                ringStrs.add(encRing(rings[r]));
            }
            fields.add(TextUtil.join(ringStrs, "\t"));
        }
        return TextUtil.join(fields, "|");
    }

    function encRing(ring) {
        var pts = [];
        for (var i = 0; i < ring.size(); i++) {
            var c = ring[i];
            var lonI = Math.round(c[0] * SCALE).toNumber();
            var latI = Math.round(c[1] * SCALE).toNumber();
            pts.add(lonI.toString() + "," + latI.toString());
        }
        return TextUtil.join(pts, ";");
    }

    function decodeZones(str) {
        var zones = [];
        var zstrs = TextUtil.split(str, "\n");
        for (var i = 0; i < zstrs.size(); i++) {
            var fields = TextUtil.split(zstrs[i], "|");
            if (fields.size() < 2) { continue; }
            var polys = [];
            for (var p = 1; p < fields.size(); p++) {
                var ringStrs = TextUtil.split(fields[p], "\t");
                var rings = [];
                for (var r = 0; r < ringStrs.size(); r++) {
                    rings.add(decRing(ringStrs[r]));
                }
                polys.add(rings);
            }
            zones.add({ "name" => fields[0], "polygons" => polys });
        }
        return zones;
    }

    function decRing(str) {
        var pts = TextUtil.split(str, ";");
        var ring = [];
        for (var i = 0; i < pts.size(); i++) {
            var pair = TextUtil.split(pts[i], ",");
            if (pair.size() < 2) { continue; }
            ring.add([ pair[0].toDouble() / SCALE, pair[1].toDouble() / SCALE ]);
        }
        return ring;
    }
}
