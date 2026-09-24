# Legalny Bushcraft — aplikacja na zegarki Garmin (Connect IQ)

Szkielet aplikacji towarzyszącej (`watch-app`) w Monkey C. Plan: `docs/garmin-companion-PLAN.md`.

Cel: samodzielna aplikacja na zegarku — GPS + kompas zegarka, strefy ZwL pobierane
„w locie" z BDL i cache'owane, dane dynamiczne (stopień pożarowy, zakazy wstępu) przez telefon.

## Struktura

```
garmin/
  manifest.xml            # produkty, uprawnienia, id, wersja
  monkey.jungle           # konfiguracja buildu
  source/
    ZwlApp.mc             # AppBase: GPS + kurs
    MainDelegate.mc       # nawigacja widokami + tryb debug (tap)
    StatusView.mc         # kompas / ikona statusu / banner zakazu
    DetailsView.mc        # nadlesnictwo, pozar, zakaz, diagnostyka GPS
    AppStore.mc           # wspolny stan (w tym pobrane strefy)
    Theme.mc              # kolory 1:1 z aplikacji mobilnej
    Spatial.mc            # haversine, bearing, point-in-polygon
    ZoneData.mc           # point-in-polygon + najblizsza strefa
    ZoneFetchService.mc   # pobieranie stref z BDL + cache (Storage)
    FireRiskService.mc    # stopien pozarowy (makeWebRequest)  [stub]
    BanService.mc         # zakazy wstepu (makeWebRequest)     [stub]
    Services.mc           # singletony serwisow
    TextUtil.mc           # split/join, skracanie tekstu, nazwa z linku
  resources/
    strings/strings.xml
    drawables/{launcher_icon.png,drawables.xml}
  tools/                  # generator stref (opcjonalny, pakowanie offline)
  bin/                    # build output (gitignored)
```

## Wymagania lokalne

- Garmin Connect IQ SDK (przez SDK Manager; wymaga JRE 11+).
- VS Code + oficjalne rozszerzenie "Monkey C".
- Python 3 (tylko generator).

```powershell
# PATH do bin SDK (Windows):
> for /f usebackq %i in (%APPDATA%\Garmin\ConnectIQ\current-sdk.cfg) do set CIQ_HOME=%~pi
> set PATH=%PATH%;%CIQ_HOME%\bin
```

## Build i symulator

Build uruchamiaj **z katalogu `garmin/`** — domyślny jungle liczy ścieżki `source`/`resources`
względem katalogu roboczego.

```powershell
# PATH do bin SDK (Windows):
> for /f usebackq %i in (%APPDATA%\Garmin\ConnectIQ\current-sdk.cfg) do set CIQ_HOME=%~pi
> set PATH=%PATH%;%CIQ_HOME%\bin

# symulator (osobne okno)
connectiq

# build sideload dla jednego modelu (wymaga klucza deweloperskiego)
monkeyc -d fr965 -f monkey.jungle -o bin\app.prg -y ..\developer_key.der

# uruchom w symulatorze
monkeydo bin\app.prg fr965

# paczka sklepowa (wszystkie modele z manifest.xml)
monkeyc -w -e -f monkey.jungle -o bin\zwl.iq -y ..\developer_key.der
```

Sideload na zegarek: skopiuj `bin\app.prg` do `GARMIN/APPS/` (USB/MTP) i zrestartuj zegarek.

Uwagi potwierdzone kompilatorem (SDK 9.2.0):
- Ikonę launcher trzeba zadeklarować w `resources/drawables/drawables.xml` jako `<bitmap id="LauncherIcon">` — sam PNG nie wystarczy.
- Kurs kompasu czytamy z `Position.Info.heading` (radiany), bez osobnego API magnetometru.
- `method(:callback)` działa tylko w instancji klasy — stąd serwisy są klasami z singletonami w `Services`.
- Monkey C nie robi modulo na floatach — normalizację azymutu robimy pętlą.

## Dane stref (wariant „runtime")

Strefy **nie są wbudowane w aplikację** — zegarek pobiera je z BDL dla prostokąta
wokół aktualnej pozycji (`ZoneFetchService`, zapytanie ArcGIS `esriGeometryEnvelope`,
`outSR=4326`, `f=json`, `responseType=JSON` → auto-parse). Wynik trzymany jest w RAM
i cache'owany w `Storage` (limit 128 KB / 8 KB na klucz → dzielimy na chunki po całych
strefach). Odświeżanie następuje, gdy użytkownik oddali się od centrum ostatniego pobrania.

Dlaczego tak: pełna geometria kraju to ~600 KB–2 MB, a budżet RAM watch-app na FR965 to
**768 KB** (kompilowany program z zasobami liczy się do tego limitu) — patrz pomiar
`tools/` i historia w `docs/garmin-companion-PLAN.md`. Pobieranie na żądanie skaluje się
na cały kraj przy ~120 KB aplikacji.

Offline: jeśli jest cache — używa go; jeśli nie — na ekranie komunikat „Brak danych stref".
`tools/generate_zones.py` zostaje jako opcja pakowania regionu offline (nie jest używany
w domyślnym buildzie).

## Sekrety (CI)

Workflow `garmin.yml` wymaga sekretów repo (`CIQ_DEV_KEY`, `GARMIN_USERNAME`,
`GARMIN_PASSWORD`, `CIQ_AGREEMENT_HASH`). Instrukcje generowania: sekcja 9
`docs/garmin-companion-PLAN.md`. Klucz deweloperski **nigdy** nie trafia do repo.

## Status

Działa: GPS, kompas (N wskazuje północ), kurs, pobieranie stref z BDL + cache,
point-in-polygon/najbliższa strefa, widok w strefie (check + nazwa + znaczek pożaru),
tryb debug (tap: IN/OUT, OUT liczy z Olsztyna). Do zrobienia: stopień pożarowy i zakazy
z BDL (obecnie stuby), wykrywanie dziur w poligonach (obecnie każdy pierścień = osobny
poligon), walidacja listy `iq:product`, pomiar RAM na urządzeniu. Publikacja do
Connect IQ Store jest ręczna.
