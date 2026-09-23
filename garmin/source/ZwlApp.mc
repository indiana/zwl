using Toybox.Application;
using Toybox.Position;
using Toybox.Math;
using Toybox.WatchUi;
using Toybox.System;

// Entry point aplikacji (zgodny z `entry="ZwlApp"` w manifest.xml).
class ZwlApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        // GPS: ciagle aktualizacje pozycji. Pole info.heading daje kurs
        // (z kompasu, gdy dostepny) - uzywane do obrotu igly kompasu.
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
    }

    function onStop(state) {
        Position.enableLocationEvents(Position.LOCATION_DISABLE, method(:onPosition));
    }

    function getInitialView() {
        var views = [ new StatusView(), new DetailsView() ];
        return [ views[0], new MainDelegate(views) ];
    }

    function onPosition(info as Position.Info) as Void {
        // Kurs ustawiamy zawsze - dziala takze zanim jest fiks pozycji.
        if (info.heading != null) {
            AppStore.state[:heading] = Math.toDegrees(info.heading);
        }

        var loc = info.position;
        if (loc == null) {
            // Brak fiksa GPS - pokazujemy to na ekranie jako diagnostyke.
            AppStore.state[:gps] = false;
            AppStore.requestUpdate();
            return;
        }

        var deg = loc.toDegrees();  // [lat, lon]
        var lat = deg[0];
        var lon = deg[1];
        AppStore.state[:gps] = true;
        AppStore.state[:lat] = lat;
        AppStore.state[:lon] = lon;

        // Wariant 1: strefy pobierane "w locie" (envelope wokol pozycji) + cache.
        Services.zones.ensureForPosition(lat, lon);

        try {
            var result = ZoneData.checkLocation(lat, lon);
            AppStore.state[:status] = result[:status];
            AppStore.state[:district] = result[:district];
            AppStore.state[:distanceMeters] = result[:distanceMeters];
            AppStore.state[:bearing] = result[:bearing];
            AppStore.state[:error] = null;

            // Dane dynamiczne odswiezamy tylko gdy jestesmy w strefie.
            if (result[:status] == ZoneData.STATUS_IN_ZONE) {
                Services.fireRisk.refresh(lat, lon);
                Services.bans.refresh(lat, lon);
            }
        } catch (e) {
            // Nie pozwalamy, by blad geometrii zamrozil UI.
            AppStore.state[:error] = e.toString();
        }

        AppStore.requestUpdate();
    }
}
