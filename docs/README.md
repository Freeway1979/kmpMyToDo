# KMP Best-Practice Guide

This repository is a working To-Do app **and** a guide to building Kotlin
Multiplatform apps well. Every practice below is implemented in the code and
linked from the chapter that explains it, so you can read the reasoning and
then the real implementation.

**The app:** offline-first to-dos with sync. Android uses Jetpack Compose, iOS
uses SwiftUI, and everything that isn't UI is shared Kotlin, including the
ViewModel. A small Ktor server in the repo acts as the placeholder backend.

## Chapters

| # | Chapter | You'll learn |
|---|---|---|
| 1 | [Project structure](01-project-structure.md) | Which modules exist, what to share, the dependency rule |
| 2 | [Build & tooling](02-build-and-tooling.md) | Version catalog, AGP 9 KMP plugin, Xcode integration, CI |
| 3 | [Architecture](03-architecture.md) | Layers, separate model/entity/DTO types, manual DI, scopes |
| 4 | [Persistence](04-persistence.md) | Room KMP, schema export, migrations and how to test them |
| 5 | [Networking](05-networking.md) | Ktor client, per-platform engines, shared API contract, errors |
| 6 | [Offline-first sync](06-offline-sync.md) | Source of truth, dirty flags, tombstones, conflicts, races |
| 7 | [Presentation](07-presentation.md) | Shared ViewModel, UI state, text fields, one-shot events |
| 8 | [iOS interop](08-ios-interop.md) | Exposing Kotlin to Swift, observing StateFlow, lifecycles |
| 9 | [Testing](09-testing.md) | Fakes, MockEngine, tests that run on JVM *and* iOS |
| 10 | [Lessons learned](10-lessons-learned.md) | Real bugs hit while building this app, and the fixes |
| 11 | [Roadmap](11-roadmap.md) | Practices not covered yet, and when you'd need them |

## The short version

1. **Share everything except UI.** Business rules, persistence, networking,
   sync and ViewModels live in `shared`. Platform code is UI plus a few lines
   of setup.
2. **The local database is the source of truth.** The UI never waits for the
   network; sync runs in the background.
3. **Keep platform-specific code tiny and at the edges.** Here that's the
   database file path, the HTTP engine and config — nothing else.
4. **Separate types per layer** (Room entity, wire DTO, domain model) and map
   between them in one place.
5. **Share the API contract with the backend** so client and server can't
   drift apart.
6. **Run shared tests on every target.** The same tests run on the JVM and
   the iOS simulator; Kotlin/Native has its own behaviour.
7. **Test on real devices.** Several bugs in [Lessons learned](10-lessons-learned.md)
   passed all unit tests and only showed up on a phone.

## Running everything

```bash
./gradlew :server:run                                  # placeholder API on :8080
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :server:test  # all tests
./gradlew :androidApp:installDebug                     # Android
adb reverse tcp:8080 tcp:8080                          # let the device reach the server
open iosApp/iosApp.xcodeproj                           # iOS: Run in Xcode
```

## Keeping this guide current

When code changes, update the chapter that describes it in the same commit.
If you learn something the hard way, add it to [Lessons learned](10-lessons-learned.md).
