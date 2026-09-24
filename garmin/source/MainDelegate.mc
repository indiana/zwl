using Toybox.WatchUi;
import Toybox.Lang;

// Nawigacja miedzy widokami + tryb debug.
//
// Na FR965 tapniecie ekranu mapuje sie na `onSelect` (BehaviorDelegate nie
// dostarcza surowego `onTap`), dlatego:
//   - tap / START  -> przelaczenie trybu debug (IN/OUT niezaleznie od GPS),
//   - gora/dol lub swipe -> zmiana widoku (StatusView <-> DetailsView).
class MainDelegate extends WatchUi.BehaviorDelegate {
    var views;
    var index = 0;

    function initialize(views) {
        BehaviorDelegate.initialize();
        self.views = views;
    }

    function onNextPage() {
        index = (index + 1) % views.size();
        WatchUi.switchToView(views[index], self, WatchUi.SLIDE_LEFT);
        return true;
    }

    function onPreviousPage() {
        index = (index + views.size() - 1) % views.size();
        WatchUi.switchToView(views[index], self, WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onSelect() {
        AppStore.cycleDebug();
        // W DEBUG: OUT pobierz strefy dla Olsztyna — pozwala przetestowac
        // sciezke sieciowa bez czekania na fiks GPS.
        if (AppStore.debug == 2) {
            Services.zones.ensureForPosition(AppStore.DEBUG_LAT, AppStore.DEBUG_LON);
        }
        return true;
    }
}
