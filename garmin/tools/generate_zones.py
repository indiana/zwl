#!/usr/bin/env python3
"""Generator zasobu stref ZwL dla aplikacji Garmin Connect IQ.

Wejscie:  GeoJSON z BDL (WFS_BDL_mapa_turystyczna, layer 76) albo lokalny plik.
Wyjscie:  garmin/source/generated/ZoneDataGenerated.mc (literaly Monkey C).

Dlaczego tak: pelne WKT stref sa za duze dla zegarka, a Monkey C nie ma w stdlib
wygodnego parsera JSON. Generujemy wiec zrodlo Monkey C z juz uproszczonymi
poligonami, ktore kompiluje sie razem z aplikacja.

Zaleznosci: tylko biblioteka standardowa (urllib, json). Uproszczenie geometrii
to wlasna implementacja Douglas-Peucker.

Uzycie:
    python garmin/tools/generate_zones.py \
        --input bdl_zwl.geojson \
        --output garmin/source/generated/ZoneDataGenerated.mc \
        --tolerance 0.0005

    # bez --input pobiera z BDL
    python garmin/tools/generate_zones.py --output garmin/source/generated/ZoneDataGenerated.mc
"""

import argparse
import datetime
import json
import sys
import urllib.request

BDL_URL = (
    "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/"
    "WFS_BDL_mapa_turystyczna/MapServer/76/query"
    "?where=1%3D1&outFields=link,nzw_ob&maxAllowableOffset=0.0001&f=geojson"
)


def fetch(url):
    print("Pobieram: %s" % url, file=sys.stderr)
    with urllib.request.urlopen(url, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def perpendicular_distance(pt, start, end):
    (x, y), (x1, y1), (x2, y2) = pt, start, end
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return ((x - x1) ** 2 + (y - y1) ** 2) ** 0.5
    t = ((x - x1) * dx + (y - y1) * dy) / float(dx * dx + dy * dy)
    px, py = x1 + t * dx, y1 + t * dy
    return ((x - px) ** 2 + (y - py) ** 2) ** 0.5


def douglas_peucker(points, tolerance):
    """Uproszczenie lancucha punktow. `tolerance` w stopniach (lat/lon)."""
    if len(points) < 3:
        return points
    start, end = points[0], points[-1]
    max_dist = 0.0
    index = 0
    for i in range(1, len(points) - 1):
        d = perpendicular_distance(points[i], start, end)
        if d > max_dist:
            max_dist, index = d, i
    if max_dist > tolerance:
        left = douglas_peucker(points[: index + 1], tolerance)
        right = douglas_peucker(points[index:], tolerance)
        return left[:-1] + right
    return [start, end]


def extract_district(props):
    """Odtwarza GeoJsonToWkt.extractForestDistrict z modulu shared."""
    link = (props or {}).get("link")
    if link:
        host = link
        for prefix in ("https://", "http://"):
            if host.startswith(prefix):
                host = host[len(prefix):]
        host = host.split("/")[0]
        parts = [p for p in host.split(".") if p and p != "www"]
        if parts:
            name = parts[0]
            return "Nadlesnictwo " + (name[:1].upper() + name[1:])
    for key in ("nadlesnictw", "nadlesnictwo", "nazwa_nadl", "nazwa", "nadl", "district", "nzw_ob"):
        value = (props or {}).get(key)
        if value:
            return value
    return "Nadlesnictwo (Nieznane)"


def simplify_closed_ring(points, tolerance):
    """Uproszczenie pierscienia zamknietego (bez duplikatu ostatniego punktu).

    DP wymaga roznych koncow, dlatego dzielimy pierscien na dwa luki w punkcie
    najdalszym od p0 i upraszczamy kazdy luk osobno.
    """
    n = len(points)
    if n < 4:
        return points
    p0 = points[0]
    far = 1
    best = -1.0
    for i in range(1, n):
        d = (points[i][0] - p0[0]) ** 2 + (points[i][1] - p0[1]) ** 2
        if d > best:
            best = d
            far = i
    arc1 = points[0: far + 1]
    arc2 = points[far:] + [p0]
    s1 = douglas_peucker(arc1, tolerance)
    s2 = douglas_peucker(arc2, tolerance)
    ring = s1[:-1] + s2
    if len(ring) >= 2 and ring[-1] == ring[0]:
        ring = ring[:-1]
    return ring


def clean_ring(coords, tolerance, precision):
    pts = [(round(float(c[0]), precision), round(float(c[1]), precision)) for c in coords]
    if len(pts) >= 4 and pts[0] == pts[-1]:
        pts = pts[:-1]
    if not pts:
        return []
    # Usun powtorzone kolejne punkty.
    dedup = [pts[0]]
    for p in pts[1:]:
        if p != dedup[-1]:
            dedup.append(p)
    pts = dedup
    if len(pts) < 3:
        return []
    simplified = simplify_closed_ring(pts, tolerance)
    if len(simplified) < 3:
        # Pierscien mniejszy niz tolerancja - pomijamy (inaczej fallback do
        # pelnej geometrii powodowal, ze wieksza tolerancja dawala wiecej punktow).
        return []
    return simplified + [simplified[0]]


def to_polygons(geom, tolerance, precision):
    """GeoJSON Polygon/MultiPolygon -> lista poligonow (lista pierscieni)."""
    if not geom:
        return []
    gtype = (geom.get("type") or "").lower()
    coords = geom.get("coordinates") or []
    raw_polys = []
    if gtype == "polygon":
        raw_polys = [coords]
    elif gtype == "multipolygon":
        raw_polys = coords
    else:
        return []
    polygons = []
    for poly in raw_polys:
        rings = []
        for ring in poly:
            cleaned = clean_ring(ring, tolerance, precision)
            if cleaned:
                rings.append(cleaned)
        if rings:
            polygons.append(rings)
    return polygons


def mc_number(value):
    text = ("%f" % value).rstrip("0").rstrip(".")
    return text if text else "0"


def mc_ring(ring):
    return "[" + ", ".join("[%s, %s]" % (mc_number(x), mc_number(y)) for x, y in ring) + "]"


def mc_zone(zone):
    name = zone["name"].replace("\\", "\\\\").replace('"', '\\"')
    polygons = []
    for poly in zone["polygons"]:
        polygons.append("[" + ", ".join(mc_ring(r) for r in poly) + "]")
    return (
        '        {\n'
        '            "name" => "%s",\n'
        '            "polygons" => [\n'
        '                %s\n'
        '            ]\n'
        "        }" % (name, ",\n                ".join(polygons))
    )


HEADER = """// ============================================================================
// PLIK GENEROWANY - nie edytuj recznie.
// Generator: garmin/tools/generate_zones.py
// Wygenerowano: %s
// Zrodlo: %s
// Tolerancja uproszczenia: %s st.  |  Precyzja zapisu: %s miejsc
// Stref: %d  |  Lacznie punktow: %d
// ============================================================================
"""


def main():
    parser = argparse.ArgumentParser(description="Generuje ZoneDataGenerated.mc")
    parser.add_argument("--input", help="lokalny GeoJSON; bez tego pobiera z BDL")
    parser.add_argument("--output", default="garmin/source/generated/ZoneDataGenerated.mc")
    parser.add_argument("--tolerance", type=float, default=0.0005,
                        help="tolerancja Douglas-Peucker w stopniach (~55 m dla 0.0005)")
    parser.add_argument("--precision", type=int, default=5,
                        help="liczba miejsc po przecinku przy zapisie wspolrzednych")
    args = parser.parse_args()

    source = "BDL: " + BDL_URL if not args.input else args.input
    if not args.input:
        data = fetch(BDL_URL)
    else:
        # "utf-8-sig" tolerates a BOM (GeoJSON saved by Windows tools often has one).
        with open(args.input, "r", encoding="utf-8-sig") as handle:
            data = json.load(handle)

    features = data.get("features") or []
    zones = []
    total_points = 0
    for feature in features:
        props = feature.get("properties") or {}
        polygons = to_polygons(feature.get("geometry"), args.tolerance, args.precision)
        if not polygons:
            continue
        for poly in polygons:
            for ring in poly:
                total_points += len(ring)
        zones.append({"name": extract_district(props), "polygons": polygons})

    body = "module ZoneDataGenerated {\n    const ZONES = [\n"
    body += ",\n".join(mc_zone(z) for z in zones)
    body += "\n    ];\n}\n"

    header = HEADER % (
        datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M UTC"),
        source,
        args.tolerance,
        args.precision,
        len(zones),
        total_points,
    )

    with open(args.output, "w", encoding="utf-8") as out:
        out.write(header + body)

    print("Zapisano %s: %d stref, %d punktow" % (args.output, len(zones), total_points), file=sys.stderr)


if __name__ == "__main__":
    main()
