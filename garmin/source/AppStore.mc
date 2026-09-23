using Toybox.WatchUi;

// Wspolny, mutowalny stan aplikacji. Trzymany jako modul, aby widoki nie musialy
// rzutowac Application.getApp() na konkretny typ.
//
// state:
//   :status         -> ZoneData.STATUS_*
//   :district       -> String lub Null
//   :distanceMeters -> Float
//   :bearing        -> Float (stopnie, 0 = polnoc)
//   :heading        -> Float lub Null (kurs zegarka, stopnie)
//   :fireRisk       -> Int lub Null (kody BDL: 0..3, 10..13, -1, -2)
//   :ban            -> Dictionary lub Null (powod, district, endDate)
//   :gps            -> Boolean (czy mamy fiks pozycji)
//   :lat / :lon     -> Double lub Null (do diagnostyki)
//   :error          -> String lub Null (blad logiki/geometrii)
module AppStore {
    var state = {
        :status         => ZoneData.STATUS_EMPTY,
        :district       => null,
        :distanceMeters => 0.0,
        :bearing        => 0.0,
        :heading        => null,
        :fireRisk       => null,
        :ban            => null,
        :gps            => false,
        :lat            => null,
        :lon            => null,
        :error          => null
    };

    // Strefy dla biezacego obszaru (pobrane z BDL lub z cache w Storage).
    // null = jeszcze nie mamy danych.
    var zones = null;

    // Stan pobierania: 0 = idle/ok, 1 = pobieranie, 2 = blad.
    var zoneState = 0;

    // Tryb debug: 0 = off, 1 = wymus widok IN, 2 = wymus widok OUT.
    // Przelaczany tapnieciem (MainDelegate.onSelect).
    var debug = 0;

    // Punkt testowy dla DEBUG: OUT (Olsztyn) — bez GPS.
    const DEBUG_LAT = 53.7784;
    const DEBUG_LON = 20.4801;

    function requestUpdate() {
        WatchUi.requestUpdate();
    }

    function cycleDebug() {
        debug = (debug + 1) % 3;
        WatchUi.requestUpdate();
    }
}
