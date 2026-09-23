using Toybox.Graphics;
using Toybox.WatchUi;

// Widok szczegolow (przelaczany z StatusView gestem/klawiszem):
// nadlesnictwo, stopien pozarowy, dane zakazu, znacznik swiezosci.
// Uwaga: brak przewijania - upraszczamy do kilku linii.
class DetailsView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var st = AppStore.state;
        var inZone = (st[:status] == ZoneData.STATUS_IN_ZONE);

        dc.setColor(Theme.background(inZone), Graphics.COLOR_TRANSPARENT);
        dc.clear();

        var font = Graphics.FONT_XTINY;
        var lineH = dc.getFontHeight(font) + 4;
        var y = lineH;

        dc.setColor(Theme.TEXT, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, y, font, "SZCZEGOLY STREFY", Graphics.TEXT_JUSTIFY_CENTER);
        y += lineH * 2;

        dc.setColor(Theme.FOREST_GREEN_TEXT, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, y, font, "Nadlesnictwo:", Graphics.TEXT_JUSTIFY_CENTER);
        y += lineH;
        dc.setColor(Theme.TEXT, Graphics.COLOR_TRANSPARENT);
        var districtText = (st[:district] == null) ? "-" : TextUtil.shortDistrict(st[:district]);
        dc.drawText(w / 2, y, font, TextUtil.fit(dc, districtText, font, (w * 0.9).toNumber()),
            Graphics.TEXT_JUSTIFY_CENTER);
        y += lineH * 2;

        var level = st[:fireRisk];
        dc.setColor(Theme.riskColor(level), Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, y, font, "Zagrozenie: " + riskText(level), Graphics.TEXT_JUSTIFY_CENTER);
        y += lineH * 2;

        // Diagnostyka GPS + stref (przydatne, gdy nic sie nie pokazuje).
        dc.setColor(Theme.TEXT_MUTED, Graphics.COLOR_TRANSPARENT);
        if (st[:gps]) {
            dc.drawText(w / 2, y, font, "GPS: fiks", Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            dc.drawText(w / 2, y, font, "GPS: brak fiksa", Graphics.TEXT_JUSTIFY_CENTER);
        }
        y += lineH;
        var zc = (AppStore.zones == null) ? -1 : AppStore.zones.size();
        dc.drawText(w / 2, y, font, "strefy: " + zc + " (state " + AppStore.zoneState + ")",
            Graphics.TEXT_JUSTIFY_CENTER);
        y += lineH;
        if (st[:lat] != null && st[:lon] != null) {
            dc.drawText(w / 2, y, font, st[:lat].toString() + ", " + st[:lon].toString(),
                Graphics.TEXT_JUSTIFY_CENTER);
            y += lineH;
        }
        if (st[:error] != null) {
            dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, y, font, "ERR: " + st[:error], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineH;
        }

        if (st[:ban] != null) {
            dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, y, font, "ZAKAZ WSTEPU", Graphics.TEXT_JUSTIFY_CENTER);
            y += lineH;
            dc.setColor(Theme.TEXT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, y, font, st[:ban][:reason], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineH;
            if (st[:ban][:endDate] != null) {
                dc.drawText(w / 2, y, font, "do " + st[:ban][:endDate], Graphics.TEXT_JUSTIFY_CENTER);
                y += lineH;
            }
        }

        if (Services.fireRisk.isStale() || Services.bans.isStale()) {
            dc.setColor(Theme.TEXT_MUTED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h - lineH, font, "Dane nieaktualne / offline", Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    function riskText(level) {
        if (level == null) { return "brak danych"; }
        if (level == -1) { return "brak polaczenia"; }
        if (level == -2) { return "brak danych z serwisu"; }
        return "stopien " + (level % 10).toString() + (level >= 10 ? " (archiwalne)" : "");
    }
}
