# Generator zasobu stref ZwL (`ZoneDataGenerated.mc`)

Zegarek nie zmieści pełnych poligonów stref ZwL z BDL, a Monkey C nie ma w stdlib
wygodnego parsera JSON. Dlatego geometrię **wbudowujemy w aplikację** jako
wygenerowane źródło Monkey C z już uproszczonymi i skwantowanymi poligonami.

## Wejście

- GeoJSON z BDL: `WFS_BDL_mapa_turystyczna`, layer 76 (ten sam endpoint co
  `BdlArcgisApi.getZanocujWLesieZones()` w `shared`), albo lokalny plik GeoJSON.
- Pole nazwy nadleśnictwa: logika 1:1 z `GeoJsonToWkt.extractForestDistrict`
  (`link` → host → pierwsza etykieta, z fallbackiem na `nzw_ob` itd.).

## Wyjście

`garmin/source/generated/ZoneDataGenerated.mc`:

```monkey
module ZoneDataGenerated {
    const ZONES = [
        {
            "name" => "Nadlesnictwo XYZ",
            "polygons" => [
                [                       // jeden poligon
                    [ [lon,lat], ... ], // pierscien zewnetrzny
                    [ [lon,lat], ... ]  // (opcjonalnie) dziury
                ]
            ]
        }
    ];
}
```

## Użycie

```bash
# Wymaga tylko Pythona 3 (bez zewnetrznych bibliotek).
python garmin/tools/generate_zones.py --input bdl_zwl.geojson \
    --output garmin/source/generated/ZoneDataGenerated.mc --tolerance 0.0005

# Bez --input skrypt pobierze GeoJSON z BDL.
python garmin/tools/generate_zones.py \
    --output garmin/source/generated/ZoneDataGenerated.mc
```

Parametry:

| Parametr | Domyślnie | Znaczenie |
|---|---|---|
| `--input` | (BDL) | lokalny GeoJSON; brak = pobierz z BDL |
| `--output` | `garmin/source/generated/ZoneDataGenerated.mc` | plik wynikowy |
| `--tolerance` | `0.0005` (~55 m) | tolerancja Douglas–Peucker w stopniach |
| `--precision` | `5` | miejsca po przecinku przy zapisie współrzędnych |

## Strojenie i pomiar

- Zbyt duży plik / problem z pamięcią na zegarku → zwiększ `--tolerance`
  (np. `0.001` ≈ 110 m) i/lub zmniejsz `--precision`.
- Po wygenerowaniu **zmierz** rozmiar `.prg`/pamięć aplikacji w symulatorze
  (`File → View memory`). To jest twardy limit — patrz sekcja 8 planu.
- Punkt wewnątrz strefy liczymy jak `Spatial.pointInRing`: ray casting.

## TODO (kolejne iteracje)

1. Ograniczenie danych do wybranych regionów (paczki) zamiast całego kraju.
2. Zapis skwantowany do liczb całkowitych (np. `deg * 1e5`) zamiast floatów —
   mniejszy plik i mniejsza niepewność na 32-bitowych floatach.
3. Odległość do **krawędzi** strefy (nie tylko do wierzchołków) + filtr bbox.
4. Generowanie także listy bounding-boxów (indeks) dla szybkiego wyszukiwania.
