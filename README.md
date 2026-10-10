# My To-Do — Kotlin Multiplatform

To-do app with a shared Kotlin core and native UIs: Jetpack Compose on Android, SwiftUI on iOS.

## Layout

| Module | What lives there |
| --- | --- |
| `shared/` | **Everything except UI**: model, business rules (validation, sorting, filtering, search, summary/empty-state text), Room KMP database, repository, `TodoListViewModel` (androidx KMP ViewModel), DI container, iOS bridge (`SharedApp`, `TodoListViewModelOwner`) |
| `androidApp/` | Compose UI only (`TodoListScreen.kt`) |
| `iosApp/` | SwiftUI UI only; `TodoListModel.swift` adapts the Kotlin `StateFlow` to `@Observable` |

## Build & run

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test   # shared tests on JVM + iOS
./gradlew :androidApp:installDebug                                     # Android
open iosApp/iosApp.xcodeproj                                           # iOS (Run in Xcode)
```

The Xcode build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so Kotlin changes are picked up automatically.

Toolchain: Kotlin 2.4.21, AGP 9.4.1 (`com.android.kotlin.multiplatform.library`), Gradle 9.7.1, Room 2.8.5, Compose BOM 2026.09.00, compileSdk 37, iOS 17+.
