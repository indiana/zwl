using Toybox.Graphics;

// Pomocnicze funkcje tekstowe dla widokow i cache'u.
module TextUtil {
    // "Nadleśnictwo Białowieża (DEMO)" -> "Białowieża"
    // (usuwa prefiks "Nadleśnictwo"/"Nadlesnictwo" i nawias na koncu).
    function shortDistrict(name) {
        if (name == null) { return ""; }
        var s = name;
        // Prefiks "Nadleśnictwo"/"Nadlesnictwo" — wykrywamy przez find == 0
        // (w Monkey C `==` na Stringach to porownanie referencji, nie tresci).
        var n1 = s.find("Nadl");
        var n2 = s.find("nadl");
        if ((n1 != null && n1 == 0) || (n2 != null && n2 == 0)) {
            var sp = s.find(" ");
            if (sp != null && sp > 0) {
                s = s.substring(sp + 1, s.length());
            }
        }
        var paren = s.find(" (");
        if (paren != null && paren > 0) {
            s = s.substring(0, paren);
        }
        return s;
    }

    // "https://kaliska.gdansk.lasy.gov.pl/..." -> "Kaliska"
    function districtFromLink(link) {
        if (link == null) { return ""; }
        var host = link;
        if (host.find("https://") != null && host.find("https://") == 0) {
            host = host.substring(8, host.length());
        } else if (host.find("http://") != null && host.find("http://") == 0) {
            host = host.substring(7, host.length());
        }
        var slash = host.find("/");
        if (slash != null) {
            host = host.substring(0, slash);
        }
        var parts = split(host, ".");
        for (var i = 0; i < parts.size(); i++) {
            var p = parts[i];
            if (p.length() > 0 && !p.equals("www")) {
                if (p.length() == 1) { return p.toUpper(); }
                return p.substring(0, 1).toUpper() + p.substring(1, p.length());
            }
        }
        return "";
    }

    // Skraca tekst wielokropkiem "..", aby zmiescil sie w `maxWidth` pikseli.
    function fit(dc, text, font, maxWidth) {
        if (text == null) { return ""; }
        if (dc.getTextWidthInPixels(text, font) <= maxWidth) {
            return text;
        }
        var s = text;
        while (s.length() > 1) {
            s = s.substring(0, s.length() - 1);
            var candidate = s + "..";
            if (dc.getTextWidthInPixels(candidate, font) <= maxWidth) {
                return candidate;
            }
        }
        return "..";
    }

    // Monkey C nie ma String.split — wlasna implementacja.
    function split(s, delim) {
        var out = [];
        var rest = s;
        var dlen = delim.length();
        while (true) {
            var i = rest.find(delim);
            if (i == null) {
                out.add(rest);
                break;
            }
            out.add(rest.substring(0, i));
            rest = rest.substring(i + dlen, rest.length());
        }
        return out;
    }

    function join(arr, delim) {
        var s = "";
        for (var i = 0; i < arr.size(); i++) {
            if (i > 0) { s += delim; }
            s += arr[i];
        }
        return s;
    }
}
