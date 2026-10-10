# My To-Do — a Kotlin Multiplatform best-practice guide

An offline-first To-Do app with sync, built with Kotlin Multiplatform.
**Everything except the UI is shared**: business rules, the Room database, the
Ktor networking, the sync engine and the ViewModel. Android uses Jetpack
Compose, and iOS uses SwiftUI.

The app also serves as a guide: **[read the guide →](docs/README.md)**

| Module | Contents |
|---|---|
| [`shared/`](shared) | model, rules, Room KMP, Ktor client, sync engine, `TodoListViewModel`, DI |
| [`contract/`](contract) | API DTOs and routes, shared by the apps and the server |
| [`server/`](server) | placeholder Ktor backend (file-backed, no auth) |
| [`androidApp/`](androidApp) | Compose UI only |
| [`iosApp/`](iosApp) | SwiftUI UI only |

## Quick start

```bash
./gradlew :server:run                    # API on http://localhost:8080
./gradlew :androidApp:installDebug
adb reverse tcp:8080 tcp:8080            # device/emulator → your Mac's localhost
open iosApp/iosApp.xcodeproj             # then Run (⌘R)
```

The apps work fully without the server: changes are kept locally and sync
when it's reachable.

## Tests

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :server:test
```

Shared tests run on both the JVM and the iOS simulator. CI runs these and
builds both apps on every pull request.

## Stack

Kotlin 2.4 · AGP 9.4 (`com.android.kotlin.multiplatform.library`) · Gradle 9.7 ·
Room 2.8 · Ktor 3.6 · kotlinx.serialization · lifecycle-viewmodel 2.11 ·
Compose BOM 2026.09 · SwiftUI (iOS 17+)
