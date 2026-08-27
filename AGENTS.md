# Agent Instructions

## Build Commands

- Gradle: always use `timeout: 900000` (15 min). On Windows invoke `.\gradlew.bat`; on CI `./gradlew`.
- Android verify (lint/typecheck): `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
- Android tests only: `.\gradlew.bat :app:testDebugUnitTest`
- iOS (KMP framework + SwiftUI app) builds happen ONLY on GitHub Actions `macos-latest` — never attempt locally on Windows.

## Project Conventions

- Integration branch: `multiplatform` (FAZY 0-3 merged). Current phase: FAZA 4 (iOS) — branch off `multiplatform` (e.g. `multiplatform/ios-app`).
- `multiplatform-PLAN.md` and `FAZY3-plan.md` are in .gitignore — never commit them.
- Hilt kept alongside Koin — Hilt for UI DI, Koin for shared module.
- No local Mac: iOS is CI-only. **Simulator-only for now** (no Apple Developer/TestFlight); device QA (compass/GPS/perf) deferred.
- iOS toolchain decisions (see plan FAZA 4): **XcodeGen** (`xcodegen generate` on the runner, no committed `.xcodeproj`); **SKIE** for Swift↔Kotlin flows/suspend; MapLibre iOS via SPM; shared KMP framework via `:shared:embedAndSignAppleFrameworkForXcode`, `baseName = "shared"`, `isStatic = true`.
- Offline tiles: shared packer lives in `shared/.../shared/offline/` (`MbtilesTilePackager`, `TileMath`); platform adapters per platform — `:app` has `OkHttpTileFetcher` + `SqliteMbtilesStore`. iOS will add its own adapters.
- OSM raster style JSON + camera constants live in `shared/.../shared/map/MapStyle.kt`.
- When encountering compatibility issues between dependencies (e.g. SDK version mismatches), always **pause and present options to the user** before choosing a direction. Do not make major decisions (upgrades, downgrades, architectural changes) autonomously.