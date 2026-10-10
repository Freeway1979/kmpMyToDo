# 11. Roadmap: practices not covered yet

These are intentionally left out to keep the sample readable. Each entry says
when you'd need it.

| Practice | Add it when | Notes |
|---|---|---|
| **Koin / kotlin-inject** for DI | many screens, feature modules | the manual `AppContainer` maps one-to-one onto a Koin module |
| **SKIE** | the Swift bridge grows past a few classes | Flow → AsyncSequence, sealed → enum; check Kotlin version support |
| **Background sync** (WorkManager, BGTaskScheduler) | changes must sync while the app is closed | call `SyncManager.sync()` from a worker or task |
| **Delta sync** with cursors and server tombstones | large data sets, or extra safety against server data loss | replaces full-snapshot comparison |
| **Server-assigned versions** instead of `updatedAt` | device clocks can't be trusted | the server increments a version; the client sends the version it saw |
| **Auth** (Ktor `Auth` plugin, bearer tokens with refresh) | real users | store tokens in Keychain / EncryptedSharedPreferences |
| **Logging** (Kermit) and crash reporting | anything beyond a demo | one logger API across platforms |
| **Shared resources and localisation** (Compose Multiplatform resources, moko-resources) | translated apps | display strings currently come from `TodoRules` in English |
| **Compose Multiplatform** shared UI | you'd rather not maintain two UIs | this guide keeps native UIs on purpose; both approaches are valid |
| **Convention plugins** (`build-logic/`) | more than three or four KMP modules | removes repeated `android {}` and target setup |
| **Feature modules** (`:feature:todo`, `:core:database`, …) | multiple teams or features | the current package layout maps directly onto modules |
| **Room `AutoMigration`** | adding columns or tables | v1→v2 needed a manual migration only because the primary key type changed |
| **Navigation** | more than one screen | e.g. Compose Navigation on Android, NavigationStack on iOS, with shared route definitions |
