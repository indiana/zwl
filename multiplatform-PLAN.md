# Multiplatform Migration Plan — Legalny Bushcraft

> **Status:** FAZY 0-1.2 ukończone (częściowo), FAZA 1.3 w toku
> **Ostatnia aktualizacja:** 2026-08-25
> **Główny cel:** Port aplikacji na iOS (KMP + SwiftUI + MapLibre)
> **Strategia branchy:** `main` nietknięte → `multiplatform` integration branch → feature branches per faza

---

## Strategia branchy

```
main (production - NIETKNIĘTE do końca)
 │
 └── multiplatform (integration branch - long-lived)
      │
      ├── multiplatform/cr-refactor (FAZA 0)
      │    └── merge → multiplatform (po CI success)
      │
      ├── multiplatform/kmp-shared (FAZA 1-2)
      │    └── merge → multiplatform (po CI success)
      │
      ├── multiplatform/maplibre-android (FAZA 3)
      │    └── merge → multiplatform (po CI success)
      │
      ├── multiplatform/ios-app (FAZA 4)
      │    └── merge → multiplatform (po CI success)
      │
      └── multiplatform/cicd (FAZA 5)
           └── merge → multiplatform (po CI success)
           └── merge → main (NA SAMYM KOŃCU, po pełnym teście)
```

### Zasady
1. `main` = production, nigdy nie merge'ujemy do niego do końca projektu
2. Każda faza ma osobny branch z `multiplatform/` prefixem
3. Merge do `multiplatform` dopiero po pozytywnym CI (GitHub Actions)
4. Jeśli bug w produkcji w międzyczasie: fix na `main`, potem rebase `multiplatform`
5. Plan w `.gitignore` — nie commitujemy

---

## Technologie (docelowe)

| Element | Android (obecnie) | iOS (docelowo) | Shared (KMP) |
|---|---|---|---|
| UI | Jetpack Compose | SwiftUI | — |
| DI | Hilt | — | Koin |
| Baza danych | Room | — | SQLDelight |
| HTTP | Retrofit + OkHttp | — | Ktor Client |
| Mapy | Mapsforge | MapLibre GL Native | — |
| Spatial | JTS (Java) | — | kts (KMP port) |
| Offline maps | Filesystem PNG | MBTiles | — |
| Logging | e.printStackTrace() | — | println() / KMP logging |
| Coroutines→Swift | — | SKIE | — |

---

## FAZA 0: Code Review Refactor

**Branch:** `multiplatform/cr-refactor`
**Czas oszacowany:** 3-5 dni
**Cel:** Wyczyścić architekturę PRZED KMP — usunąć naruszenia warstwowości, podzielić God Object

### 0.1: Domain→data repository interfaces

**Status:** ✅ Zakończone (commit `32f66e6`)

Cel: Use cases w domain nie mogą importować z data. Tworzymy repo interfaces.

**Nowe pliki do utworzenia:**
- ✅ `domain/repository/ZoneRepository.kt` — interface
- ✅ `domain/repository/PoiRepository.kt` — interface
- ✅ `domain/repository/ForestBanRepository.kt` — interface
- ⬜ `domain/repository/ForestStandRepository.kt` — interface (nie jest używany, pominięty)
- ✅ `data/repository/ZoneRepositoryImpl.kt` — implementacja
- ✅ `data/repository/PoiRepositoryImpl.kt` — implementacja
- ✅ `data/repository/ForestBanRepositoryImpl.kt` — implementacja
- ⬜ `data/repository/ForestStandRepositoryImpl.kt` — implementacja (nie jest używany, pominięty)

**Zmieniane use cases:**
- ✅ `GetZonesUseCase.kt` — `ZoneDao` → `ZoneRepository`
- ✅ `GetForestBansUseCase.kt` — `ForestBanDao` → `ForestBanRepository`
- ✅ `SyncZonesUseCase.kt` — `ZoneDao` → `ZoneRepository`
- ✅ `SyncPoiUseCase.kt` — `PoiDao` → `PoiRepository`
- ✅ `SyncForestBansUseCase.kt` — `ForestBanDao` → `ForestBanRepository`
- ✅ `GetFireRiskUseCase.kt` — via `ZoneRepository` (okrężna droga)
- ✅ `GetForestStandUseCase.kt` — via `ZoneRepository` (okrężna droga)

**Repo interfaces (wzorzec):**
```kotlin
// domain/repository/ZoneRepository.kt
interface ZoneRepository {
    suspend fun getAllZones(): List<Zone>
    suspend fun getZonesCount(): Int
    suspend fun getByForestDistrict(district: String): ZoneEntity?
    suspend fun updateFireRisk(district: String, level: Int, timestamp: Long)
    suspend fun updateForestStand(district: String, json: String, timestamp: Long)
    fun getAllZonesFlow(): Flow<List<ZoneEntity>>
}
```

**Weryfikacja:**
- ✅ `./gradlew :app:testDebugUnitTest` — testy przechodzą
- ✅ `./gradlew :app:assembleDebug` — APK się buduje
- ✅ CI success

### 0.2: Split MainViewModel (God Object)

**Status:** ✅ Zakończone (commit `2475ac4`) + fix (commit `807f8e0`)

Cel: MainViewModel (751 linii, ~15 deps) → 2-3 mniejsze ViewModel'e.

**Nowe pliki:**
- ✅ `presentation/ZoneDetailViewModel.kt` — fire risk + forest stand logic (~230 linii)
- ✅ `presentation/map/MapViewModel.kt` — offline download, map state (~75 linii)

**Zmiany w MainViewModel.kt:**
- ✅ Przenieś `selectZone()`, `selectZoneByDistrict()` → `ZoneDetailViewModel`
- ✅ Przenieś `downloadMapArea()`, `isDownloadingArea`, `downloadProgress` → `MapViewModel`
- ✅ Przenieś `selectedZoneDetails`, `selectedPoiDetails` → respective VMs
- ✅ Usuń `OkHttpClient` dependency (używane tylko w OfflineMapDownloader)
- ✅ Usuń `ZoneDao`, `PoiDao` direct deps (teraz via repositories)
- ✅ Redukcja do ~290 linii

**Docelowa struktura:**
```
MainViewModel (~290 linii):
├─ location tracking
├─ compass
├─ spatial engine orchestration
├─ zone sync orchestration
└─ UI state (MainUiState)

ZoneDetailViewModel (~230 linii):
├─ fire risk fetching
├─ forest stand fetching
├─ selectedZoneDetails state
└─ distance calculation

MapViewModel (~75 linii):
├─ offline map download
├─ download progress
└─ map center/zoom state
```

**Weryfikacja:**
- ✅ `./gradlew :app:testDebugUnitTest` — testy przechodzą
- ✅ `./gradlew :app:assembleDebug` — APK się buduje
- ✅ CI success
- ⬜ Manual test na emulatorze —一切 działa

### 0.3: Drobne zmiany (CR #6, #8, #10, #18)

**Status:** ✅ Zakończone (commity `e405d42` + `b592714`)

- ✅ `LocationStatus` → `sealed interface` + `data object EmptyData` (zamiast `sealed class`)
- ✅ `DownloadEvent` → `sealed interface` + `data object` (zamiast `object`)
- ✅ `PoiClassification` → extension function w `domain/util/PoiClassification.kt`
- ✅ `GeoJsonFeature.forestDistrict` → usunięty dead code (logika już istniała w `GeoJsonConverter.extractForestDistrict`)
- ✅ Usuń `ForestBanMapper.toEntity()` (dead code — nigdzie nie jest wywoływany)
- ✅ `fireRiskStatusText`: rozróżnienie -1 (brak połączenia) od -2 (brak danych z serwisu)

**Weryfikacja:**
- ✅ `./gradlew :app:testDebugUnitTest` — testy przechodzą
- ✅ `./gradlew :app:assembleDebug` — APK się buduje
- ✅ CI success

### Podsumowanie FAZY 0

- ✅ Wszystkie 0.1, 0.2, 0.3 zakończone
- ✅ CI success na `multiplatform-cr-refactor` (4 commity, wszystkie green)
- ⬜ Merge → `multiplatform` (oczekuje na decyzję)
- ⬜ **Gotowe do FAZY 1**

---

## FAZA 1: KMP Shared Module

**Branch:** `multiplatform/kmp-shared`
**Czas oszacowany:** 2-3 tygodnie
**Cel:** Utworzyć moduł shared z domain + data w commonMain

### 1.1: Utworzenie modułu shared

**Status:** ✅ Zakończone (commit `a09f7a8`)

- ✅ Utworzyć `shared/build.gradle.kts` z KMP plugin
- ✅ Dodać `shared` do `settings.gradle.kts`
- ⬜ Skonfigurować targets: androidTarget() ✅, iosX64/Arm64/SimulatorArm64 ⬜ (wymaga macOS)
- ✅ Dodać zależności commonMain:
  - ⬜ kts-core 1.20.0.0 — usunięte, wymaga Kotlin 2.4+ (SpatialEngine zostaje w :app)
  - ✅ SQLDelight 2.0.2, Ktor 2.3.13, kotlinx-serialization 1.6.3, Koin 3.5.6, kotlinx-coroutines
- ✅ androidTarget z JVM 17
- ⬜ iOS targets zakomentowane (Kotlin/Native compiler nie działa na Windows)

### 1.2: Przeniesienie domain layer do commonMain

**Status:** ✅ Zakończone (częściowo — pliki zależne od JTS/Gson zostają w :app do upgrade Kotlin)

**Skopiowane do shared/commonMain:**
- ✅ `domain/model/` — Zone.kt, ForestBan.kt, LocationStatus.kt, ForestStandSummary.kt, CommonLocation.kt
- ✅ `domain/repository/` — ZoneRepository.kt, ForestBanRepository.kt, PoiRepository.kt, CompassRepository.kt
- ✅ `domain/usecase/` — GetZonesUseCase.kt, GetForestBansUseCase.kt, GetFireRiskUseCase.kt
- ✅ `domain/util/` — BdlInfo.kt, NadlesnictwoUrls.kt, RdlpMapper.kt, PoiClassification.kt
- ✅ `domain/` — LocationRepository.kt (common)
- ✅ `data/local/` — PoiEntity.kt (common, bez Room annotations)
- ✅ `data/remote/` — BdlFireApi.kt (common interface), FireRiskModels.kt (kotlinx.serialization)

**Zostają w :app (zależą od JTS/Gson, przeniosą się po upgrade Kotlin 2.x):**
- ⬜ `SpatialEngine.kt` — JTS (Envelope, WKTReader, STRtree, DistanceOp)
- ⬜ `GeoJsonConverter.kt` — JTS + Gson (JsonReader, JsonToken)
- ⬜ `GetForestStandUseCase.kt` — JTS (Envelope, WKTReader)
- ⬜ `SyncZonesUseCase.kt` — JTS (WKTWriter) + ZoneEntity + GeoJsonConverter
- ⬜ `SyncForestBansUseCase.kt` — JTS (WKTWriter) + ForestBanEntity + GeoJsonConverter
- ⬜ `SyncPoiUseCase.kt` — PoiEntity (Room) + BdlArcgisApi + Gson

**Zamiana JTS → kts:**
- ⬜ kts-core 1.20.0.0 wymaga Kotlin 2.4+ (metadata version 2.4.0, nasz compiler 1.9.23 czyta do 2.0.0)
- ⬜ SpatialEngine i GeoJsonConverter przeniosą się po upgrade Kotlin do 2.x

### 1.2.5: Upgrade Kotlin 1.9.23 → 2.x

**Status:** ✅ Częściowo zakończone (Phase A-C done, Phase D zablokowany)

**Cel:** Odblokować kts-core dla SpatialEngine w shared + zaktualizować cały toolchain.

**Decyzja:** Użyto Kotlin 2.1.21 zamiast 2.4.10, ponieważ kts-core 1.20.0.0 wymaga Kotlin 2.4+ (metadata 2.4.0), a Hilt 2.56.2 nie czyta metadata 2.4.0. Aby odblokować kts-core, potrzebny jest upgrade do AGP 9.0+ (Hilt 2.60.1+, Kotlin 2.4+).

**Phase A: Gradle + AGP (niskie ryzyko)** ✅
- ✅ Upgrade Gradle 8.7 → 8.14.1 (wrapper)
- ✅ Upgrade AGP 8.3.2 → 8.13.2 (`libs.versions.toml`)
- ✅ CI fix: `./gradlew` zamiast `gradle`, gradle/actions/setup-gradle@v4

**Phase B: Kotlin + plugins (średnie ryzyko)** ✅
- ✅ Upgrade Kotlin 1.9.23 → 2.1.21
- ✅ Upgrade KSP 1.9.23-1.0.20 → 2.1.21-2.0.2
- ✅ Dodaj plugin `org.jetbrains.kotlin.plugin.compose`
- ✅ Usuń `composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }` z `app/build.gradle.kts`
- ✅ Compose BOM 2024.06.00 → 2024.09.03 (2025.05.00 wymaga compileSdk 37 + AGP 9.1+)
- ✅ Hilt 2.51.1 → 2.56.2 (2.60.1 wymaga AGP 9.0+)
- ✅ Hilt navigation-compose 1.4.0 → 1.2.0, hilt-work 1.4.0 → 1.2.0 (wymagały compileSdk 37)
- ✅ Migrate kotlinOptions.jvmTarget → compilerOptions DSL (wymagane przez Kotlin 2.x)
- ✅ kotlinx-coroutines 1.7.3 → 1.9.0
- ✅ kotlinx-serialization 1.6.0 → 1.7.3

**Phase C: Fix + weryfikacja** ✅
- ✅ Clean build, fix K2 compiler warnings (fallbackToDestructiveMigration, ExperimentalCoroutinesApi, LinearProgressIndicator lambda)
- ✅ Wszystkie 55 testów przechodzą
- ✅ APK się buduje
- ✅ CI green na GitHub Actions

**Phase D: kts-core + SpatialEngine** ⬜ ZABLOKOWANY
- ⬜ Blokada: kts-core 1.20.0.0 wymaga metadata 2.4.0 → Kotlin 2.4+ → Hilt 2.60.1+ → AGP 9.0+ → Gradle 9.0+
- ⬜ Odblokowanie wymaga upgrade AGP 8.13.2 → 9.x (duże zmiany w API/DSL)
- ⬜ Alternatywa: użyć starszej wersji kts-core kompatybilnej z Kotlin 2.1.x (wymaga weryfikacji)

**Weryfikacja:** ✅
- ✅ `gradle :app:testDebugUnitTest` — testy przechodzą
- ✅ `gradle :app:assembleDebug` — APK się buduje
- ✅ CI green na GitHub Actions

### 1.3: Przeniesienie data layer do commonMain

**Status:** ⬜ Nie rozpoczęto

**SQLDelight (zamiast Room):**
- ⬜ Utworzyć `shared/src/commonMain/sqldelight/com/indiana/zwl/data/local/`
- ⬜ Napisać schematy SQL:
  - ⬜ `Zone.sq` — ZoneEntity schema + queries
  - ⬜ `Poi.sq` — PoiEntity schema + queries
  - ⬜ `ForestBan.sq` — ForestBanEntity schema + queries
- ⬜ Skonfigurować `sqldelight {}` block w build.gradle.kts
- ⬜ Utworzyć `expect/actual DatabaseDriverFactory`:
  - ⬜ `commonMain`: `expect class DatabaseDriverFactory { expect fun createDriver(): SqlDriver }`
  - ⬜ `androidMain`: `actual class DatabaseDriverFactory(context: Context) { actual fun createDriver() = AndroidSqliteDriver(...) }`
  - ⬜ `iosMain`: `actual class DatabaseDriverFactory { actual fun createDriver() = NativeSqliteDriver(...) }`

**Ktor Client (zamiast Retrofit):**
- ⬜ Utworzyć `shared/src/commonMain/.../data/remote/`
- ⬜ `BdlOgcApi.kt` → Ktor `HttpClient` (shared)
- ⬜ `BdlFireApi.kt` → Ktor `HttpClient` (shared)
- ⬜ `BdlArcgisApi.kt` → Ktor `HttpClient` (shared)
- ⬜ Zamiana `Gson` → `kotlinx.serialization` dla modeli API
- ⬜ expect/actual dla HTTP engine:
  - ⬜ `commonMain`: `expect fun httpClient(): HttpClient`
  - ⬜ `androidMain`: `actual fun httpClient() = HttpClient(OkHttp)`
  - ⬜ `iosMain`: `actual fun httpClient() = HttpClient(Darwin)`

**Repository implementations:**
- ⬜ `data/repository/ZoneRepositoryImpl.kt` → shared (SQLDelight queries)
- ⬜ `data/repository/PoiRepositoryImpl.kt` → shared
- ⬜ `data/repository/ForestBanRepositoryImpl.kt` → shared
- ⬜ `data/mapper/` → shared (ZoneMapper, ForestBanMapper)
- ⬜ `data/sync/SyncWorker.kt` → expect/actual (Android: WorkManager, iOS: BGTaskScheduler)

### 1.4: Koin DI modules

**Status:** ⬜ Nie rozpoczęto

- ⬜ Utworzyć `shared/src/commonMain/.../di/SharedModule.kt`
- ⬜ Utworzyć `shared/src/androidMain/.../di/AndroidModule.kt`
- ⬜ Utworzyć `shared/src/iosMain/.../di/IosModule.kt`
- ⬜ Skonfigurować Koin initialization:
  - ⬜ Android: `startKoin { modules(sharedModule, androidModule) }` w `ZwlApplication.kt`
  - ⬜ iOS: `initKoin()` function callable z Swift

**Weryfikacja:**
- ⬜ `./gradlew :shared:compileKotlinAndroid` — kompiluje się
- ⬜ `./gradlew :app:assembleDebug` — APK z shared module buduje się

---

## FAZA 2: Android Build Test

**Branch:** kontynuacja `multiplatform/kmp-shared`
**Czas oszacowany:** 2-3 dni
**Cel:** Upewnić się że Android build po refaktorze działa

### 2.1: Podpięcie app/ pod shared

**Status:** ⬜ Nie rozpoczęto

- ⬜ `app/build.gradle.kts`: dodać `implementation(project(":shared"))`
- ⬜ Usunąć stare zależności z `app/build.gradle.kts`:
  - ⬜ Room (+ KSP processor)
  - ⬜ Retrofit + OkHttp + Gson converter
  - ⬜ JTS (jts-core)
  - ⬜ Hilt (zastąpione przez Koin)
- ⬜ Zaktualizować importy w `app/` na shared module
- ⬜ Usunąć stare pliki data layer z `app/src/main/java/`

### 2.2: Testy i build

**Status:** ⬜ Nie rozpoczęto

- ⬜ `./gradlew :app:testDebugUnitTest` — WSZYSTKIE testy przechodzą
- ⬜ `./gradlew :app:assembleDebug` — APK się buduje
- ⬜ Manual test na emulatorze:
  - ⬜ Strefy się ładują
  - ⬜ Lokalizacja działa
  - ⬜ Kompas działa
  - ⬜ Mapa się renderuje
  - ⬜ POI się pokazują
  - ⬜ Fire risk się pobiera
  - ⬜ Forest bans się pokazują

### Podsumowanie FAZY 1-2

- ⬜ Wszystkie kroki 1.1-1.4 i 2.1-2.2 zakończone
- ⬜ CI success na `multiplatform/kmp-shared`
- ⬜ Merge → `multiplatform`
- ⬜ **Gotowe do FAZY 3**

---

## FAZA 3: MapLibre na Android

**Branch:** `multiplatform/maplibre-android`
**Czas oszacowany:** 1 tydzień
**Cel:** Zamiana Mapsforge na MapLibre Android SDK

### 3.1: Dodanie MapLibre SDK

**Status:** ⬜ Nie rozpoczęto

- ⬜ `app/build.gradle.kts`: dodać:
  ```kotlin
  implementation("org.maplibre.gl:android-sdk:11.6.1")
  implementation("org.maplibre.gl:maplibre-plugins-android:5.0.0")
  ```
- ⬜ Usunąć Mapsforge dependencies:
  - ⬜ `org.mapsforge:mapsforge-map`
  - ⬜ `org.mapsforge:mapsforge-themes`
  - ⬜ `com.caverock:androidsvg`
- ⬜ Usunąć ProGuard rules dla Mapsforge

### 3.2: Rewrite MapViewContainer.kt

**Status:** ⬜ Nie rozpoczęto

- ⬜ MapLibre `MapView` zamiast forkowanego `MapView.java`
- ⬜ `SymbolManager` zamiast custom Marker classes
- ⬜ `FillManager` zamiast custom Polygon classes
- ⬜ GeoJSON source zamiast programmatic polygon drawing
- ⬜ OnMapClickListener zamiast SafeLayer tap interception
- ⬜ MapLibre `Projection` zamiast `MercatorProjection`

### 3.3: Offline maps → MBTiles

**Status:** ⬜ Nie rozpoczęto

- ⬜ MapLibre `OfflineManager` zamiast `OfflineMapDownloader`
- ⬜ `OfflineTilePyramidRegionDefinition` do definiowania regionów
- ⬜ MBTiles cache zamiast filesystem PNG cache

### 3.4: Usunięcie Mapsforge

**Status:** ⬜ Nie rozpoczęto

- ⬜ Usunąć `RotatingMarker.kt` (zastąpiony przez Symbol rotation)
- ⬜ Usunąć `ZoomAwareMarker.kt` (zastąpiony przez data-driven styling)
- ⬜ Usunąć `SafeLayer.java` (zastąpiony przez MapLibre click listeners)
- ⬜ Usunąć `SafePolygon.java` (zastąpiony przez FillManager)
- ⬜ Usunąć forked `MapView.java` (`org/mapsforge/map/android/view/`)
- ⬜ Usunąć vendored `AndroidUtil.java` i inne `org/mapsforge/` pliki
- ⬜ Usunąć `MapTileCache.kt` (MapLibre ma own cache)
- ⬜ Usunąć `BitmapUtils.kt` (MapLibre ma own bitmap handling)

### 3.5: Testy i build

**Status:** ⬜ Nie rozpoczęto

- ⬜ `./gradlew :app:testDebugUnitTest` — testy przechodzą
- ⬜ `./gradlew :app:assembleDebug` — APK się buduje
- ⬜ Manual test na emulatorze:
  - ⬜ Mapa się renderuje (online tiles)
  - ⬜ Strefy (polygony) się pokazują
  - ⬜ Bany (czerwone polygony) się pokazują
  - ⬜ Markery POI działają
  - ⬜ Strzałka kompasu rotuje
  - ⬜ Zoom-dependent markers działają
  - ⬜ Tap na strefę/ban działa
  - ⬜ Offline download działa (MBTiles)

### Podsumowanie FAZY 3

- ⬜ Wszystkie kroki 3.1-3.5 zakończone
- ⬜ CI success na `multiplatform/maplibre-android`
- ⬜ Merge → `multiplatform`
- ⬜ **Gotowe do FAZY 4**

---

## FAZA 4: iOS App

**Branch:** `multiplatform/ios-app`
**Czas oszacowany:** 2-3 tygodnie
**Cel:** Utworzyć natywną aplikację iOS w SwiftUI z MapLibre

### 4.1: Xcode project setup

**Status:** ⬜ Nie rozpoczęto

- ⬜ Utworzyć `iosApp/` directory
- ⬜ Utworzyć Xcode project (`.xcodeproj`)
- ⬜ Dodać shared KMP framework jako dependency (SPM lub CocoaPods)
- ⬜ Skonfigurować Info.plist:
  - ⬜ `NSLocationWhenInUseUsageDescription`
  - ⬜ `NSLocationAlwaysUsageDescription`
- ⬜ Dodać MapLibre GL Native via SPM

### 4.2: SwiftUI Views

**Status:** ⬜ Nie rozpoczęto

- ⬜ `App.swift` — entry point, Koin initialization
- ⬜ `Views/MainView.swift` — ekran główny
- ⬜ `Views/MapView.swift` — MapLibre wrapper (UIViewRepresentable)
- ⬜ `Views/InZoneView.swift` — widok "jesteś w strefie"
- ⬜ `Views/OutsideZoneView.swift` — widok "poza strefą"
- ⬜ `Views/ZoneDetailView.swift` — szczegóły strefy
- ⬜ `Views/ForestBanDetailView.swift` — szczegóły zakazu
- ⬜ `Views/PermissionsView.swift` — prośba o permisje
- ⬜ `ViewModels/MainViewModel.swift` — binding do shared KMP code

### 4.3: MapLibre iOS

**Status:** ⬜ Nie rozpoczęto

- ⬜ MapLibre `MGLMapView` (iOS)
- ⬜ Polygon rendering via GeoJSON
- ⬜ Marker/Symbol rendering
- ⬜ Tap handling (delegate pattern)
- ⬜ Offline MBTiles (MLNOfflineStorage)
- ⬜ Rotacja markera (compass heading)

### 4.4: xcodebuild test

**Status:** ⬜ Nie rozpoczęto

- ⬜ `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator` — kompiluje się
- ⬜ Build artifact (.app) jest generowany

### Podsumowanie FAZY 4

- ⬜ Wszystkie kroki 4.1-4.4 zakończone
- ⬜ CI success na `multiplatform/ios-app`
- ⬜ Merge → `multiplatform`
- ⬜ **Gotowe do FAZY 5**

---

## FAZA 5: CI/CD

**Branch:** `multiplatform/cicd`
**Czas oszacowany:** 2-3 dni
**Cel:** GitHub Actions workflow dla iOS build

### 5.1: iOS workflow

**Status:** ⬜ Nie rozpoczęto

- ⬜ Utworzyć `.github/workflows/ios.yml`
- ⬜ Runner: `macos-latest`
- ⬜ Steps:
  - ⬜ Checkout
  - ⬜ Setup JDK 17 (dla KMP shared build)
  - ⬜ Build shared framework: `./gradlew :shared:linkDebugFrameworkIosArm64`
  - ⬜ Setup Xcode
  - ⬜ Build iOS app: `xcodebuild ... -sdk iphonesimulator`
  - ⬜ Upload artifact

### 5.2: Apple Developer (opcjonalnie)

**Status:** ⬜ Nie rozpoczęto

- ⬜ Zakup Apple Developer Program ($99/rok)
- ⬜ Utworzyć iOS Development Certificate
- ⬜ Utworzyć Provisioning Profile
- ⬜ Dodać secrets do GitHub:
  - ⬜ `APPLE_CERTIFICATE` (base64 .p12)
  - ⬜ `APPLE_CERT_PASSWORD`
  - ⬜ `APPLE_PROVISIONING_PROFILE` (base64)
- ⬜ Zaktualizować `ios.yml` o code signing
- ⬜ Test: install na fizycznym urządzeniu via TestFlight

### Podsumowanie FAZY 5

- ⬜ Wszystkie kroki 5.1-5.2 zakończone
- ⬜ CI success na `multiplatform/cicd`
- ⬜ Merge → `multiplatform`
- ⬜ Merge `multiplatform` → `main` ← **KONIEC PROJEKTU**

---

## Zagadnienia otwarte / Notatki

### Biblioteki KMP — weryfikacja kompatybilności

| Biblioteka | KMP support | Status |
|---|---|---|
| kts-core (JTS port) | JVM, JS, Wasm, Native | ✅ Weryfikacja: `de.mpmediasoft.kts:kts-core:1.20.0.0` |
| SQLDelight | JVM, Android, iOS, JS, Wasm | ✅ `app.cash.sqldelight:2.3.2` |
| Ktor Client | JVM, Android, iOS, JS, Wasm | ✅ Engine: Darwin (iOS), OkHttp (Android) |
| Koin | JVM, Android, iOS | ✅ `io.insert-koin:koin-core:4.0.0` |
| MapLibre GL Native | Android, iOS | ✅ osobne SDK per platform |
| SKIE / KMP-NativeCoroutines | iOS flow bridging | ⬜ Do weryfikacji przy integracji |

### Kluczowe ryzyka

1. **kts-core na Kotlin/Native** — weryfikować czy `STRtree` (spatial index) działa na Native
2. **SQLDelight schema migration** — Room → SQLDelight wymaga napisania migracji lub destructive migration
3. **MapLibre offline MBTiles** — weryfikować czy tile format jest kompatybilny z obecnymi danymi
4. **iOS code signing bez Maca** — GitHub Actions `macos-latest` ma Xcode, ale physical device testing wymaga Apple Developer

### Linki referencyjne

- [JetBrains KMP + Ktor + SQLDelight tutorial](https://kotlinlang.org/docs/multiplatform/multiplatform-ktor-sqldelight.html)
- [kts-core (JTS KMP port)](https://github.com/mipastgt/kts)
- [SQLDelight Native driver docs](https://sqldelight.github.io/sqldelight/native_sqlite/)
- [Ktor Client engines](https://ktor.io/docs/client-engines.html)
- [MapLibre Android SDK](https://maplibre.org/maplibre-native/android/)
- [MapLibre iOS SDK](https://maplibre.org/maplibre-native/ios/)
- [KMP-NativeCoroutines](https://github.com/rickclephas/KMP-NativeCoroutines)
- [SKIE (Touchlab)](https://github.com/touchlab/SKIE)
