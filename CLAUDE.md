# kmpMyToDo

A KMP To-Do app that doubles as a **best-practice guide** (`docs/`). The code
and the guide must stay in sync.

## Rules

- **Update the docs with every change.** If you change how something works,
  update the chapter in `docs/` that describes it in the same commit. New
  practice → new section, or a row in `docs/11-roadmap.md` if not implemented.
- **Hard-won lessons go in `docs/10-lessons-learned.md`** (symptom, cause, fix).
- **Share everything except UI.** New logic goes in `shared/`; the apps only
  lay out `TodoListUiState` and call ViewModel intents.
- **Dependency rule:** `presentation → domain ← data`. Nothing in `domain/`
  imports from `data/`.
- **API changes go through `contract/`** and must stay backward compatible
  (new fields optional with defaults).
- **Schema changes:** bump the Room version, add a migration in
  `data/local/Migrations.kt`, commit the exported schema JSON, and extend
  `MigrationTest`.

## Verify

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :server:test :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

For behaviour changes, also run the apps against `./gradlew :server:run`
(Android: `adb reverse tcp:8080 tcp:8080`). When automating Android, set
`ANDROID_SERIAL` to an emulator: the developer's personal phone may be
connected over USB.
