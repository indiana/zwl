using Toybox.Communications;
import Toybox.Lang;
using Toybox.System;
using Toybox.WatchUi;

// Zakazy wstepu do lasu z BDL (WMS_zakazy_wstepu_do_lasu).
// Te same pola co BdlArcgisApi.getForestBans (app/.../shared/data/remote).
//
// Zakazy sa danymi dynamicznymi i krytycznymi dla bezpieczenstwa:
//  - cache w Storage + znacznik swiezosci,
//  - odfiltruj wygasle po `data_koncowa`,
//  - przy braku sieci pokaz ostatni cache z wyraznym "Dane nieaktualne".
// Instancja singletonu: `Services.bans`.
class BanService {
    const URL = "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_zakazy_wstepu_do_lasu/MapServer/0/query";
    const PAGE_SIZE = 500;

    var bans = null;        // Array<Dictionary> lub Null
    var fetchedAt = null;   // ms

    function initialize() {
    }

    function isStale() {
        if (fetchedAt == null) { return true; }
        return (System.getTimer() - fetchedAt) > (24 * 60 * 60 * 1000);
    }

    function refresh(lat, lon) {
        if (!isStale()) { return; }
        // TODO: zawezic zapytanie po bbox (lat/lon) zamiast ciagnac caly kraj,
        //       oraz paginowac PAGE_SIZE (jak SyncForestBansUseCase).
        var params = {
            "where"          => "1=1",
            "outFields"      => "objectid,kod_nadl,nazwa_nadl,kod,opis,data,data_koncowa",
            "returnGeometry" => true,
            "f"              => "geojson"
        };
        Communications.makeWebRequest(URL, params, {}, method(:onResponse));
    }

    function onResponse(responseCode as Number, data as Dictionary or String or Null) as Void {
        // TODO: sparsowac GeoJSON, zbudowac liste zakazow, sprawdzic point-in-polygon
        //       (Spatial.pointInRing) i ustawic AppStore.state[:ban] gdy jestesmy w zakazie.
        if (responseCode == 200 && data != null) {
            fetchedAt = System.getTimer();
        }
        WatchUi.requestUpdate();
    }
}
