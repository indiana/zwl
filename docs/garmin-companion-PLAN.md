# Plan: aplikacja towarzysząca na zegarki Garmin (Connect IQ)

> Status: **plan zatwierdzony do realizacji — wariant A+ w monorepo.**
> Data planu: 2026-09-14. Dotyczy aplikacji "Legalny Bushcraft" (Zanocuj w Lesie).
> Dokument jest planem; szkielet kodu powstaje w katalogu `garmin/` (nie-Gradle).

## 1. Cel

Dostarczyć aplikację **Connect IQ** na zegarek Garmin, która działając samodzielnie
(bazując na GPS + kompasie zegarka) pokazuje:

1. **Poza strefą ZwL:** kompas z odległością i kierunkiem do najbliższej strefy.
2. **W strefie ZwL:** ikonę statusu + nadleśnictwo + stopień zagrożenia pożarowego.
3. **Widok szczegółów** (przełączalny z obu powyższych): nadleśnictwo (strefy, w której
   jestem, albo tej, do której wskazuje kompas), stopień zagrożenia pożarowego i — jeśli
   się zmieści — pozostałe dane strefy.
4. **Zakazy wstępu do lasu:** czerwony alert nadrzędny nad oboma widokami, z powodem,
   okresem obowiązywania i danymi nadleśnictwa.

Kolorystyka spójna z aplikacją mobilną (`app/.../presentation/theme/Theme.kt`).

**Non-goals (na teraz):**
- Pełne dane drzewostanu/SILP na zegarku (zbyt duże; patrz wariant B w sekcji 11).
- Most BLE przez Connect IQ Mobile SDK i zmiany w aplikacji mobilnej.
- Działanie na zegarkach bez magnetometru (kompasu) — patrz sekcja 8.

## 2. Decyzja architektoniczna

Wybrano **wariant A+**:

- Geometria stref ZwL **wbudowana w aplikację** w postaci uproszczonej, wygenerowanej
  na etapie buildu (generator w `garmin/tools/`).
- Dane dynamiczne (**stopień pożarowy** i **zakazy wstępu**) pobierane w runtime przez
  `Communications.makeWebRequest` z BDL, z cache lokalnym i wskaźnikiem świeżości.
- Telefon jest **wyłącznie bramką do internetu** (Garmin Connect Mobile). Aplikacja
  mobilna ZwL nie jest w tym wariancie potrzebna ani modyfikowana.
- Kod trzymamy w **monorepo**, w katalogu `garmin/`, który **nie jest modułem Gradle**
  (osobny toolchain `monkeyc`/Jungle). Dzięki temu build Androida/iOS pozostaje
  nietknięty, a `settings.gradle.kts` nie wymaga zmian.

Uzasadnienie odrzucenia innych wariantów znajduje się w sekcji 11 (rozważone
alternatywy i koszty).

## 3. Stos technologiczny

| Element | Wybór |
|---|---|
| SDK | Garmin Connect IQ SDK (pin wersji, bazowo 9.2.0) |
| Język | Monkey C |
| Typ aplikacji | `watch-app` (Device App) |
| UI | `WatchUi.View` + rysowanie imperatywne przez `Graphics.Dc`; `resources/` (strings, drawables) |
| GPS | `Toybox.Position` (`enableLocationEvents`, `LOCATION_CONTINUOUS`) |
| Kompas | `Toybox.Sensor` (`SENSOR_MAGNETOMETER`, `Sensor.Info.heading`) |
| Sieć | `Toybox.Communications.makeWebRequest` (idzie przez telefon) |
| Matematyka | `Toybox.Math` (`atan2`, `toDegrees`, `sin`, `cos`) |
| Build | `monkeyc` + `monkey.jungle`; symulator `connectiq` + `monkeydo` |
| IDE | VS Code + oficjalne rozszerzenie "Monkey C" (JRE 11+) |
| CI | GitHub Actions (workflow `garmin.yml`), build podpisany `.prg`/`.iq` jako artefakt |

## 4. Architektura aplikacji na zegarku

```
garmin/
  manifest.xml                 # produkt(y), uprawnienia, id, ikona, wersja
  monkey.jungle                # konfiguracja buildu
  source/
    ZwlApp.mc                  # AppBase: inicjalizacja, rejestracja GPS/sensorów
    MainDelegate.mc            # BehaviorDelegate: nawigacja między widokami
    StatusView.mc              # widok główny: poza strefą / w strefie
    DetailsView.mc             # widok szczegółów (nadleśnictwo, pożar, zakaz)
    Theme.mc                   # kolory 1:1 z Theme.kt
    Spatial.mc                 # haversine, initial bearing, point-in-polygon
    ZoneData.mc                # model stref + checkLocation()
    ZoneDataGenerated.mc       # DANE GENEROWANE (uporządkowane poligony)
    FireRiskService.mc         # makeWebRequest -> stopień pożarowy + cache
    BanService.mc              # makeWebRequest -> zakazy + cache
  resources/
    strings/strings.xml
    drawables/launcher_icon.png
    data/                      # (opcjonalnie surowe dane, jeśli nie generujemy .mc)
  tools/
    generate_zones.py          # generator zasobu stref (BDL GeoJSON -> .mc)
    GENERATOR.md               # spec formatu i użycia
  bin/                         # output buildu (gitignored)
```

Przepływ danych:

```
GPS (Position.Info) ──┐
                      ├─► ZoneData.checkLocation() ─► InZone / OutsideZone
magnetometr (heading)─┘                                   │
                                                          ▼
                                        StatusView (kompas / ikona statusu)
                                                          │
FireRiskService/BanService (makeWebRequest, cache) ───────┴─► DetailsView / BanAlert
```

Port logiki z `shared`:
- `SpatialEngine.calculateHaversineDistance` / `calculateInitialBearing` → `Spatial.mc`.
- `SpatialEngine.checkLocation` (point-in-polygon + najbliższa strefa) → `ZoneData.mc`.
- `SpatialEngine.checkForestBan` → `BanService.mc` (ray-casting, jak w `SpatialEngine.kt:77-103`).
- Progi cache 24 h i mapowanie kodów pożaru (0–3, 10–13, -1/-2) → jak w
  `ZoneDetailViewModel` / `fireRiskStatusText`.

## 5. UI i mapowanie kolorystyki

Kolumny kolorów przeniesione 1:1 z `app/.../theme/Theme.kt` do `Theme.mc` / `colors.xml`:

| Nazwa | Hex |
|---|---|
| GreenPrimary | `0xFF2E7D32` |
| GreenSecondary | `0xFF1B5E20` |
| GreenBackground | `0xFF0C190D` |
| GreenSurface | `0xFF162D18` |
| GreenText | `0xFFE8F5E9` |
| YellowPrimary | `0xFFFBC02D` |
| YellowSecondary | `0xFFF57F17` |
| YellowBackground | `0xFF131313` |
| YellowSurface | `0xFF1A1A1A` |
| YellowText | `0xFFFFFDE7` |
| ForestGreenAccent | `0xFF81C784` |
| ForestGreenText | `0xFFA5D6A7` |
| ErrorDarkBackground | `0xFF261010` |
| ErrorRedAccent | `0xFFEF5350` |
| ErrorRedButton | `0xFFC62828` |
| RiskLevelNone | `0xFF81C784` |
| RiskLevelLow | `0xFFFFF176` |
| RiskLevelMedium | `0xFFFFB74D` |
| RiskLevelHigh | `0xFFE57373` |
| RiskLevelUnknown | `0xFFB0BEC5` |
| AmberAccent | `0xFFFFB300` |

Uwagi implementacyjne:
- Ekrany są niemal zawsze **okrągłe** — układ liczony z `dc.getWidth()/dc.getHeight()`,
  z marginesem ~10% (nie wchodzić w obszar przycięty). Uwzględnić ekrany prostokątne
  (np. Venu/inne), jeśli dodamy je do `manifest.xml`.
- Trzy "strony" przełączane gestem/klawiszem: `StatusView` ⇄ `DetailsView`; przy aktywnym
  zakazie `StatusView` rysuje czerwony banner, a `DetailsView` pełne dane zakazu.

## 6. Dane i generator

### 6.1 Format i generacja

Pełne `geometryWkt` z BDL są za duże dla zegarka (limity pamięci aplikacji). Dlatego:

1. Generator pobiera GeoJSON z warstwy ZwL (`WFS_BDL_mapa_turystyczna`, layer 76 —
   ten sam endpoint co `BdlArcgisApi.getZanocujWLesieZones()`).
2. Upraszcza poligony (Douglas–Peucker, tolerancja startowa ~30–80 m).
3. Kwantyzuje współrzędne (np. 1e5 = ~1 m) i zapisuje jako **generowany plik Monkey C**
   `source/generated/ZoneDataGenerated.mc` z literałami tablic (unika parsowania JSON w
   runtime, które w Monkey C jest kosztowne/nie ma go w stdlib).
4. Nazwy nadleśnictw zapisywane wprost (stringi).

Specyfikacja formatu i użycie: `garmin/tools/GENERATOR.md`.
Szkielet generatora: `garmin/tools/generate_zones.py`.
Plik `ZoneDataGenerated.mc` w repo to **placeholder** z jedną strefą demonstracyjną,
żeby szkielet dało się uruchomić w symulatorze.

### 6.2 Dane dynamiczne (pożar, zakazy)

- `FireRiskService`: `makeWebRequest` do
  `https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_zagrozenie_pozarowe_w_lasach/MapServer/0/query`
  (pola `kod,opis`), mapowanie kodów jak w `fireRiskStatusText`.
- `BanService`: `makeWebRequest` do
  `.../WMS_zakazy_wstepu_do_lasu/MapServer/0/query` (pola jak w
  `BdlArcgisApi.getForestBans`), paginacja 500.
- **Cache** lokalny + timestamp; próg świeżości 24 h (spójny z aplikacją mobilną).
- **Wskaźnik świeżości** na `DetailsView` ("dane z dnia X") — zakazy są krytyczne dla
  bezpieczeństwa, więc nie wolno sugerować aktualności danych, których nie mamy.
- Filtrowanie wygasłych zakazów po `data_koncowa`.
- `makeWebRequest` działa tylko na pierwszym planie — odświeżanie przy otwarciu aplikacji.

## 7. Build i deployment

Lokalnie (Windows):

```powershell
# klucz deweloperski (raz; patrz sekcja 9)
openssl genrsa -out developer_key.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt

# sideload na konkretny model
monkeyc -d fenix7 -f garmin/monkey.jungle -o garmin/bin/app.prg -y developer_key.der

# paczka sklepowa dla wszystkich modeli z manifestu
monkeyc -w -e -f garmin/monkey.jungle -o garmin/bin/app.iq -y developer_key.der
```

Sideload na zegarek: skopiować `.prg` do `GARMIN/APPS/` (USB/MTP) i zrestartować;
albo przez aplikację Connect IQ na telefonie.

Dystrybucja: **Connect IQ Store**, publikacja **ręczna** (dashboard "Submit an App",
upload `.iq`, opis, screeny). Brak odpowiednika TestFlight; opcjonalnie prywatny listing
beta. Build/artefakt robi CI, publikacja pozostaje krokiem człowieka.

## 8. Ograniczenia i ryzyka

1. **Kompas nie na każdym zegarku** — lista `iq:product` w `manifest.xml` musi zawierać
   tylko modele z magnetometrem; runtime fallback na `Position.Info` (track), gdy
   `Sensor.Info.heading == null`.
2. **Budżet pamięci** — bardzo ciasny. Stąd uproszczona geometria i brak pełnych danych
   drzewostanu. Realny rozmiar `ZoneDataGenerated.mc` trzeba zmierzyć na docelowych modelach.
3. **Świeżość danych** — pożar/zakazy wymagają telefonu z internetem. Cache + wskaźnik
   świeżości, czytelne "BRAK DANYCH".
4. **Dwie bazy kodu** — Monkey C nie korzysta z KMP. Generator geometrii trzymamy osobno
   i wersjonujemy razem z aplikacją mobilną.
5. **Prywatność** — `Communications` do `mapserver.bdl.lasy.gov.pl` do zadeklarowania
   w manifeście/store; uzupełnić politykę prywatności.
6. **Walidacja `iq:product`** — identyfikatory urządzeń trzeba zweryfikować z listą SDK;
   błędny ID wywala build.
7. **Sekrety** — klucz deweloperski i dane konta Garmin wyłącznie w repo secrets; nigdy
   w repo (sekcja 9).

## 9. Sekrety — co, gdzie i jak wygenerować

Sekrety trafiają do **GitHub Actions → Settings → Secrets and variables → Actions**
(repo `zwl`). Z poziomu Windows można je ustawić przez `.\assets\gh.exe`.

| Sekret | Skąd |
|---|---|
| `CIQ_DEV_KEY` | base64 z wygenerowanego `developer_key.der` (klucz podpisujący) |
| `GARMIN_USERNAME` | login konta Garmin (developer.garmin.com) |
| `GARMIN_PASSWORD` | hasło tego konta |
| `CIQ_AGREEMENT_HASH` | hash akceptacji licencji z `connect-iq-sdk-manager agreement view` |

Generowanie klucza deweloperskiego (openssl — Git Bash / WSL / `winget install ShiningLight.OpenSSL`):

```bash
openssl genrsa -out developer_key.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
```

Zapis sekretów (PowerShell, z katalogu repo):

```powershell
# 1) Klucz -> base64 -> sekret (stdin)
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\developer_key.der"))
$b64 | .\assets\gh.exe secret set CIQ_DEV_KEY

# 2) Konto Garmin
.\assets\gh.exe secret set GARMIN_USERNAME
.\assets\gh.exe secret set GARMIN_PASSWORD

# 3) Hash zgody (uzyskany lokalnie z CLI, patrz niżej)
.\assets\gh.exe secret set CIQ_AGREEMENT_HASH
```

Uzyskanie `CIQ_AGREEMENT_HASH` lokalnie:

```bash
# zainstaluj CLI (wymaga Go): go install github.com/lindell/connect-iq-sdk-manager-cli@latest
connect-iq-sdk-manager agreement view     # odczytaj i skopiuj hash akceptacji
```

Ważne:
- `developer_key.pem`/`.der` **nigdy** do repo — są w `.gitignore` (`garmin/*.der`, `*.pem`).
- Zrób **backup `.pem`** (np. w menedżerze haseł). Ten sam klucz musi podpisywać wszystkie
  przyszłe aktualizacje tego listingu w Connect IQ Store; nie da się go odtworzyć.
- Na czas developmentu lokalnego klucz trzymaj poza repo (np. `%USERPROFILE%\.garmin\`).

## 10. CI (`garmin.yml`) — zakres

Workflow uruchamiany **ręcznie** (`workflow_dispatch`, bo wymaga sekretów i zwalidowanej
listy urządzeń). Kroki:

1. Checkout + JDK 17.
2. Weryfikacja obecności sekretów (czytelny błąd, jeśli brak).
3. Instalacja `connect-iq-sdk-manager` (`go install`, deterministyczne).
4. `agreement accept` + `login` + `sdk set` + `device download --manifest`.
5. Dekodowanie `CIQ_DEV_KEY` do `developer_key.der`.
6. `monkeyc` → `.prg` (sideload) oraz `.iq` (store).
7. Upload artefaktów.

Publikacja do store poza CI (ręczna).

## 11. Rozważone alternatywy (dlaczego A+)

| Wariant | Opis | Dlaczego nie teraz |
|---|---|---|
| A (standalone bez sieci) | tylko dane wbudowane | brak świeżych zakazów/pożaru; A+ to tanie rozszerzenie |
| B (most BLE przez apkę) | Connect IQ Mobile SDK, liczy JTS na telefonie | ~2–3× koszt A+ (integracja SDK Android+iOS, protokół, lifecycle, 3 codebase'y, iOS CI-only) |
| C (serwer-proxy) | własny backend liczy geometrię | utrzymanie serwera bez realnej korzyści |
| Submodule / osobne repo | wydzielenie `garmin/` | sprzężenie i tak przez dane; brak korzyści, gorsze CI/sekret/strategia branchy |

Wariant A+ pokrywa większość wartości przy ~1/3 kosztu B i bez dotykania aplikacji mobilnej.

## 12. Roadmapa (MVP)

1. **[ten commit]** Plan + szkielet `garmin/` + `garmin.yml` + instrukcja sekretów.
2. Generator stref: implementacja `generate_zones.py`, wygenerowanie realnego
   `ZoneDataGenerated.mc`, pomiar rozmiaru/pamięci na wybranych modelach.
3. `Spatial.mc` + `ZoneData.mc`: pełny point-in-polygon i najbliższa strefa; testy w
   symulatorze na kilku punktach referencyjnych.
4. UI: kompas poza strefą, ikona w strefie, widok szczegółów; mapowanie kolorów.
5. `FireRiskService` + `BanService` z cache i wskaźnikiem świeżości.
6. Walidacja `iq:product`, zamknięcie listy urządzeń, testy na fizycznym zegarku.
7. Publikacja (ręczna) w Connect IQ Store.

## 13. Otwarte pytania

- Docelowa lista zegarków (które modele traktujemy jako wspierane w MVP)?
- Czy pokazywać zakazy również jako osobny "page" w karuzeli (Widget) obok Device App?
- Format widgetu glance (opcjonalny) — czy wchodzi do MVP?
