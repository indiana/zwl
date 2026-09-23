using Toybox.Graphics;

// Kolory 1:1 z app/.../presentation/theme/Theme.kt.
// W Connect IQ kolor to liczba 0xRRGGBB (bez alfy).
module Theme {
    const GREEN_PRIMARY      = 0x2E7D32;
    const GREEN_SECONDARY    = 0x1B5E20;
    const GREEN_BACKGROUND   = 0x0C190D;
    const GREEN_SURFACE      = 0x162D18;
    const GREEN_TEXT         = 0xE8F5E9;

    const YELLOW_PRIMARY     = 0xFBC02D;
    const YELLOW_SECONDARY   = 0xF57F17;
    const YELLOW_BACKGROUND  = 0x131313;
    const YELLOW_SURFACE     = 0x1A1A1A;
    const YELLOW_TEXT        = 0xFFFDE7;

    const FOREST_GREEN_ACCENT = 0x81C784;
    const FOREST_GREEN_TEXT   = 0xA5D6A7;

    const ERROR_DARK_BACKGROUND = 0x261010;
    const ERROR_RED_ACCENT      = 0xEF5350;
    const ERROR_RED_BUTTON      = 0xC62828;

    const RISK_NONE    = 0x81C784;
    const RISK_LOW     = 0xFFF176;
    const RISK_MEDIUM  = 0xFFB74D;
    const RISK_HIGH    = 0xE57373;
    const RISK_UNKNOWN = 0xB0BEC5;

    const AMBER_ACCENT = 0xFFB300;

    const TEXT        = 0xFFFFFF;
    const TEXT_MUTED  = 0x9E9E9E;

    function background(inZone) {
        return inZone ? GREEN_BACKGROUND : YELLOW_BACKGROUND;
    }

    function accent(inZone) {
        return inZone ? GREEN_PRIMARY : YELLOW_PRIMARY;
    }

    // Mapowanie kodow BDL: 0..3 swieze, 10..13 archiwalne offline,
    // -1 brak polaczenia, -2 brak danych, null nieznany.
    function riskColor(level) {
        if (level == null) { return RISK_UNKNOWN; }
        if (level == 0 || level == 10) { return RISK_NONE; }
        if (level == 1 || level == 11) { return RISK_LOW; }
        if (level == 2 || level == 12) { return RISK_MEDIUM; }
        if (level == 3 || level == 13) { return RISK_HIGH; }
        return RISK_UNKNOWN;
    }
}
