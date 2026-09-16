# <img src="assets/app_icon.png" width="48" align="center" alt="App Icon"/> Legalny Bushcraft

**Legalny Bushcraft** to aplikacja mobilna na system Android i iOS (oraz aplikacja towarzysząca na zegarki Garmin), zbudowana w oparciu o wspólny moduł Kotlin Multiplatform. Powstała z myślą o miłośnikach bushcraftu, survivalu i turystyki leśnej. Jej głównym celem jest natychmiastowe i w pełni niezawodne (również w trybie offline) udzielenie odpowiedzi na pytanie, czy użytkownik znajduje się wewnątrz oficjalnej strefy programu **„Zanocuj w lesie" (ZwL)** Lasów Państwowych, oraz jakie zasady bezpieczeństwa pożarowego obowiązują w danej lokalizacji.

> **Minimalna wersja Androida:** 8.0 (API 26) · **iOS:** 16.0

---

## 🌲 Cel i Działanie Projektu

Aplikacja została zaprojektowana w myśl zasady **Offline-First**, ponieważ w głębi lasu zasięg sieci komórkowej jest często ograniczony lub niedostępny.

* **W strefie:** Interfejs przybiera bezpieczną, zieloną oprawę, informując o nazwie nadleśnictwa, stopniu zagrożenia pożarowego (od 0 do 3) oraz o tym, czy dozwolone jest używanie turystycznych kuchenek gazowych.
* **Poza strefą:** Interfejs zmienia się na ostrzegawczy (żółto-pomarańczowy) i działa jako nawigator – wskazuje precyzyjną odległość do najbliższego obszaru legalnego biwakowania oraz fizyczny kierunek świata (wraz z dynamiczną strzałką kompasu).
* **Interaktywna mapa:** Umożliwia wyświetlanie granic stref ZwL jako półprzezroczystych wielokątów oraz lokalizacji użytkownika na rastrowym podkładzie OpenStreetMap (MapLibre) działającym całkowicie bez dostępu do Internetu (kafle offline w formacie MBTiles).

Strefy programu „Zanocuj w lesie" pobierane są z oficjalnych źródeł rządowych — serwera danych przestrzennych Lasów Państwowych: [mapserver.bdl.lasy.gov.pl](https://mapserver.bdl.lasy.gov.pl/). Informacje o zagrożeniu pożarowym pobierane są z publicznego API Banku Danych o Lasach ([lasy.gov.pl](https://www.lasy.gov.pl/)).

---

## 🛠️ Wykorzystane Technologie

Projekt oparty jest na wspólnym module **Kotlin Multiplatform**, z którego korzystają aplikacje Android i iOS:

* **Języki:** [Kotlin](https://kotlinlang.org/) (wspólny moduł `shared` + Android), [Swift](https://www.swift.org/) (iOS), Monkey C (Garmin Connect IQ).
* **Warstwa wspólna:** [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) – logika domenowa, dane, mapa i pakiety offline współdzielone między platformami.
* **UI & Prezentacja:** [Jetpack Compose](https://developer.android.com/compose) z komponentami **Material 3** (Android) oraz **SwiftUI** (iOS).
* **Baza danych:** [SQLDelight](https://cashapp.github.io/sqldelight/) – lokalny cache poligonów stref ZwL oraz metadanych nadleśnictw, wspólny dla Androida i iOS.
* **Silnik mapowy:** [MapLibre Native](https://maplibre.org/) – offline'owe renderowanie rastrowego podkładu OpenStreetMap; obszary offline pakowane do kafli **MBTiles**.
* **Obliczenia przestrzenne:** [kts – Kotlin Topology Suite](https://github.com/vespa-engine/kts) (port JTS) – operacje geometryczne (Point-in-Polygon, odległość do najbliższego wielokąta).
* **Komunikacja sieciowa:** [Ktor](https://ktor.io/) (silniki OkHttp na Androidzie i Darwin na iOS) oraz [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) – pobieranie granic stref oraz stopnia zagrożenia pożarowego z API Banku Danych o Lasach (BDL).
* **Zarządzanie zadaniami w tle:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) – okresowa synchronizacja danych o strefach oraz zagrożeniu pożarowym.
* **Wtryskiwanie zależności:** [Hilt (Dagger Hilt)](https://developer.android.com/training/dependency-injection/hilt-android) – warstwa UI na Androidzie; [Koin](https://insert-koin.io/) – wspólny moduł KMP.
* **Lokalizacja:** [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) (Android) oraz CoreLocation (iOS) – precyzyjne i zoptymalizowane pod kątem zużycia baterii pobieranie współrzędnych GPS (częstotliwość próbkowania dostosowuje się na podstawie danych z akcelerometru).

---

## 🏗️ Architektura Projektu

Repozytorium ma strukturę monorepo z wydzieloną warstwą wspólną:

```
shared/    # moduł Kotlin Multiplatform (logika współdzielona Android + iOS)
  domain/    # modele, interfejsy repozytoriów, Use Cases (obliczenia przestrzenne)
  data/      # SQLDelight (local), klient Ktor + DTO (remote), repozytoria
  map/       # styl rastrowy OSM i stałe kamery
  offline/   # pakowanie kafli offline do MBTiles
  update/    # logika aktualizacji danych stref i zagrożenia pożarowego
  di/        # moduły Koin
app/       # aplikacja Android (Jetpack Compose, Hilt, WorkManager)
iosApp/    # aplikacja iOS (SwiftUI, XcodeGen, SKIE dla Flow/suspend)
garmin/    # aplikacja towarzysząca na zegarki Garmin (Connect IQ / Monkey C)
```

Moduł `shared` przestrzega zasad **Clean Architecture** (podział na `domain`, `data` i `presentation` po stronie platform), a aplikacje platformowe pełnią rolę warstwy prezentacji.

---

## 📱 Platformy Docelowe

* **Android** – Jetpack Compose + Material 3, dystrybucja przez GitHub Actions.
* **iOS** – SwiftUI z modułem `shared` jako frameworkiem statycznym; dystrybucja przez TestFlight.
* **Garmin** – samodzielna aplikacja Connect IQ (GPS + kompas zegarka), pobierająca strefy ZwL z BDL „w locie" i cache'ująca je lokalnie. Szczegóły w [`garmin/README.md`](garmin/README.md).

---

## 📥 Wymagania Systemowe

* **Minimalna wersja Androida:** Android 8.0 (API 26)
* **Docelowa wersja Androida:** Android 16 (API 36)
* **Minimalna wersja iOS:** 16.0
* **Środowisko deweloperskie:** Java/JDK 17 + Gradle Kotlin DSL
* **CI/CD:** GitHub Actions — `android.yml`, `ios.yml`, `ios-release.yml` (TestFlight), `garmin.yml`

---

## 🔒 Prywatność

Aplikacja nie gromadzi, nie przechowuje ani nie udostępnia żadnych danych osobowych użytkowników na zewnętrznych serwerach. Dane o lokalizacji przetwarzane są wyłącznie lokalnie na urządzeniu.

Szczegóły zawiera [Polityka Prywatności](PRIVACY_POLICY.md).
