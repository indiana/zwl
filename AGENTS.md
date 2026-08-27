# Agent Instructions

## Build Commands

- Always use `timeout: 900000` (15 min) for Gradle commands (`.\gradlew.bat`). Default 120s timeout causes opencode to hang and freeze.
- Lint/typecheck: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
- Tests only: `.\gradlew.bat :app:testDebugUnitTest`

## Project Conventions

- Branch `kmp-shared` is the active KMP integration branch
- `multiplatform-PLAN.md` is in .gitignore — never commit it
- Hilt kept alongside Koin — Hilt for UI DI, Koin for shared module
- iOS targets are commented out (require macOS)
- When encountering compatibility issues between dependencies (e.g. SDK version mismatches), always **pause and present options to the user** before choosing a direction. Do not make major decisions (upgrades, downgrades, architectural changes) autonomously.
