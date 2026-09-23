using Toybox.Graphics;
using Toybox.Math;
using Toybox.WatchUi;

// Widok glowny:
//   - poza strefa: kompas (N wskazuje polnoc) + odleglosc/kierunek do strefy,
//   - w strefie: ikona statusu + nadlesnictwo + stopien pozarowy,
//   - przy zakazie: czerwony banner nadrzedny nad oboma.
class StatusView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var st = AppStore.state;

        // Tryb debug: tapniecie przelacza IN/OUT niezaleznie od pozycji GPS.
        var inZone;
        if (AppStore.debug == 1) {
            inZone = true;
        } else if (AppStore.debug == 2) {
            inZone = false;
        } else {
            inZone = (st[:status] == ZoneData.STATUS_IN_ZONE);
        }

        dc.setColor(Theme.background(inZone), Graphics.COLOR_TRANSPARENT);
        dc.clear();

        var top = 0;
        if (st[:ban] != null) {
            top = drawBanBanner(dc, w);
        }
        if (inZone) {
            drawInZone(dc, w, h, top);
        } else {
            drawOutside(dc, w, h, top);
        }

        if (AppStore.debug != 0) {
            var f = Graphics.FONT_XTINY;
            dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h - dc.getFontHeight(f), f,
                (AppStore.debug == 1 ? "DEBUG: IN" : "DEBUG: OUT"),
                Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    // ------------------------------------------------------------------ ban

    function drawBanBanner(dc, w) {
        var font = Graphics.FONT_XTINY;
        var h = dc.getFontHeight(font) + 16;
        dc.setColor(Theme.ERROR_DARK_BACKGROUND, Graphics.COLOR_TRANSPARENT);
        dc.fillRectangle(0, 0, w, h);
        dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(2);
        dc.drawLine(0, h, w, h);
        dc.drawText(w / 2, 8, font, "ZAKAZ WSTEPU DO LASU", Graphics.TEXT_JUSTIFY_CENTER);
        return h + 4;
    }

    // -------------------------------------------------------------- in zone

    function drawInZone(dc, w, h, top) {
        var cx = w / 2;
        var cy = top + (h - top) / 2 - 24;
        var r = 28;
        var gap = 6;

        // Ikona statusu (check) po lewej.
        var checkCx = cx - r - gap;
        dc.setColor(Theme.GREEN_PRIMARY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(3);
        dc.drawCircle(checkCx, cy, r);
        dc.setColor(Theme.FOREST_GREEN_ACCENT, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(checkCx - 12, cy, checkCx - 3, cy + 10);
        dc.drawLine(checkCx - 3, cy + 10, checkCx + 14, cy - 12);

        // Znaczek zagrozenia pozarowego po prawej (wypelnione kolo z numerem stopnia).
        // W trybie DEBUG: IN, gdy brak realnych danych (stub BDL), pokazujemy "2"
        // tylko po to, by ocenic wyglad znaczka.
        var level = AppStore.state[:fireRisk];
        if (AppStore.debug == 1 && level == null) {
            level = 2;
        }
        var badgeCx = cx + r + gap;
        dc.setColor(Theme.riskColor(level), Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(badgeCx, cy, r);
        var glyphFont = Graphics.FONT_MEDIUM;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.drawText(badgeCx, cy - dc.getFontHeight(glyphFont) / 2, glyphFont,
            riskGlyph(level), Graphics.TEXT_JUSTIFY_CENTER);

        // Nazwa nadlesnictwa (zamiast napisu "W STREFIE").
        var nameFont = Graphics.FONT_TINY;
        var nameY = cy + r + 14;
        var district = AppStore.state[:district];
        dc.setColor(Theme.TEXT, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, nameY, nameFont,
            TextUtil.fit(dc, TextUtil.shortDistrict(district), nameFont, (w * 0.9).toNumber()),
            Graphics.TEXT_JUSTIFY_CENTER);

        drawFireRiskText(dc, cx, nameY + dc.getFontHeight(nameFont) + 4, level);
    }

    // Glyph na znaczku zagrozenia: 0..3, albo "?" (brak danych) / "!" (brak polaczenia).
    function riskGlyph(level) {
        if (level == null) { return "?"; }
        if (level < 0) { return "!"; }
        return (level % 10).toString();
    }

    // ----------------------------------------------------------- out of zone

    function drawOutside(dc, w, h, top) {
        var st = AppStore.state;

        // Wartosci bazowe z GPS.
        var district = st[:district];
        var distance = st[:distanceMeters];
        var bearing = st[:bearing];
        var gps = st[:gps];
        var error = st[:error];

        // DEBUG: OUT - ignorujemy GPS i liczymy z Olsztyna.
        if (AppStore.debug == 2) {
            var r = ZoneData.checkLocation(AppStore.DEBUG_LAT, AppStore.DEBUG_LON);
            district = r[:district];
            distance = r[:distanceMeters];
            bearing = r[:bearing];
            gps = true;
            error = null;
        }

        var cx = w / 2;
        var available = (h - top);
        var compassR = ((w < available) ? w : available) / 4;
        var cy = top + available / 2 - compassR / 2;
        var font = Graphics.FONT_XTINY;

        // Urzadzenie patrzy na polnoc, gdy heading == null.
        var headingDeg = 0.0;
        if (st[:heading] != null) {
            headingDeg = st[:heading];
        }

        // Tarcza.
        dc.setColor(Theme.YELLOW_PRIMARY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(2);
        dc.drawCircle(cx, cy, compassR);

        // Kierunki swiata obracaja sie z kursem: N zawsze wskazuje prawdziwa polnoc.
        drawCardinal(dc, cx, cy, compassR - 10, -headingDeg + 0.0, "N", font, Theme.YELLOW_PRIMARY);
        drawCardinal(dc, cx, cy, compassR - 10, -headingDeg + 90.0, "E", font, Theme.TEXT_MUTED);
        drawCardinal(dc, cx, cy, compassR - 10, -headingDeg + 180.0, "S", font, Theme.TEXT_MUTED);
        drawCardinal(dc, cx, cy, compassR - 10, -headingDeg + 270.0, "W", font, Theme.TEXT_MUTED);

        // Igla: azymut celu wzgledem kierunku patrzenia.
        drawNeedle(dc, cx, cy, compassR - 6, bearing - headingDeg, Theme.YELLOW_PRIMARY);

        // Nadlesnictwo nad odlegloscia / diagnostyka.
        var y = cy + compassR + 10;
        if (!gps) {
            dc.setColor(Theme.RISK_MEDIUM, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "GPS: szukam sygnalu...", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AppStore.zoneState == 1) {
            dc.setColor(Theme.RISK_MEDIUM, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "Pobieranie stref...", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AppStore.zoneState == 2) {
            dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "Blad pobierania stref", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AppStore.zones == null) {
            dc.setColor(Theme.TEXT_MUTED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "Brak danych stref", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (AppStore.zones.size() == 0) {
            dc.setColor(Theme.TEXT_MUTED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "Brak stref w okolicy", Graphics.TEXT_JUSTIFY_CENTER);
        } else if (error != null) {
            dc.setColor(Theme.ERROR_RED_ACCENT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, font, "Blad danych", Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            var nameFont = Graphics.FONT_TINY;
            dc.setColor(Theme.TEXT, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, nameFont,
                TextUtil.fit(dc, TextUtil.shortDistrict(district), nameFont, (w * 0.9).toNumber()),
                Graphics.TEXT_JUSTIFY_CENTER);
            dc.setColor(Theme.RISK_LOW, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y + dc.getFontHeight(nameFont) + 2, font,
                formatDistance(distance), Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    // Rysuje litere kierunku na okregu. Kat liczony od gory, zgodnie z ruchem wskazowek.
    function drawCardinal(dc, cx, cy, radius, angleDeg, label, font, color) {
        var rad = Math.toRadians(angleDeg);
        var x = cx + (radius * Math.sin(rad));
        var y = cy - (radius * Math.cos(rad));
        var fh = dc.getFontHeight(font);
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x.toNumber(), (y - fh / 2).toNumber(), font, label, Graphics.TEXT_JUSTIFY_CENTER);
    }

    function drawNeedle(dc, cx, cy, length, angleDeg, color) {
        var rad = Math.toRadians(angleDeg);
        var s = Math.sin(rad);
        var c = Math.cos(rad);
        var halfBase = (length / 4).toNumber();
        if (halfBase < 3) { halfBase = 3; }
        var local = [
            [0, -length],
            [halfBase, halfBase],
            [0, halfBase / 2],
            [-halfBase, halfBase]
        ];
        var pts = new [local.size()];
        for (var i = 0; i < local.size(); i++) {
            var x = local[i][0];
            var y = local[i][1];
            pts[i] = [cx + (x * c - y * s), cy + (x * s + y * c)];
        }
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.fillPolygon(pts);
    }

    // --------------------------------------------------------------- shared

    function drawFireRiskText(dc, cx, y, level) {
        var font = Graphics.FONT_XTINY;
        var text;
        if (level == null) {
            text = "Pozar: brak danych";
        } else if (level == -1) {
            text = "Pozar: brak polaczenia";
        } else if (level == -2) {
            text = "Pozar: brak danych z serwisu";
        } else {
            text = "Pozar: stopien " + (level % 10).toString();
        }
        dc.setColor(Theme.riskColor(level), Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, y, font, text, Graphics.TEXT_JUSTIFY_CENTER);
    }

    function formatDistance(meters) {
        if (meters < 1000.0) {
            return meters.toNumber().toString() + " m";
        }
        var km = meters / 1000.0;
        if (km < 10.0) {
            return km.format("%.1f") + " km";
        }
        return km.toNumber().toString() + " km";
    }
}
