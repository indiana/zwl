using Toybox.Communications;
import Toybox.Lang;
using Toybox.System;
using Toybox.WatchUi;

// Stopien zagrozenia pozarowego z BDL.
// UWAGA: makeWebRequest idzie przez sparowany telefon (Garmin Connect Mobile).
// Dziala tylko na pierwszym planie. Instancja singletonu: `Services.fireRisk`.
class FireRiskService {
    const URL = "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_zagrozenie_pozarowe_w_lasach/MapServer/0/query";

    var level = null;       // Int lub Null (kody BDL)
    var fetchedAt = null;   // ms z System.getTimer()

    function initialize() {
    }

    function isStale() {
        if (fetchedAt == null) { return true; }
        return (System.getTimer() - fetchedAt) > (24 * 60 * 60 * 1000);
    }

    function refresh(lat, lon) {
        if (!isStale()) { return; }  // cache 24h
        var params = {
            "geometry"       => lat.toString() + "," + lon.toString(),
            "geometryType"   => "esriGeometryPoint",
            "inSR"           => 4326,
            "spatialRel"     => "esriSpatialRelIntersects",
            "outFields"      => "kod,opis",
            "returnGeometry" => false,
            "f"              => "geojson"
        };
        Communications.makeWebRequest(URL, params, {}, method(:onResponse));
    }

    function onResponse(responseCode as Number, data as Dictionary or String or Null) as Void {
        // TODO: sparsowac GeoJSON (pole `kod`) i ustawic `level`.
        // Monkey C nie ma parsera JSON w stdlib - potrzebny barrel lub wlasny parser.
        if (responseCode == 200 && data != null) {
            fetchedAt = System.getTimer();
        }
        WatchUi.requestUpdate();
    }
}
