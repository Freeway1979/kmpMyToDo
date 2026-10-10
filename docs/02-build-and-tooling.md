# 2. Build & tooling

## Versions live in one place

All versions are in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml),
and every module refers to them as `libs.…`. Shared numbers such as
`androidCompileSdk` and `androidMinSdk` are there too, so the four Android
targets can't disagree.

Current stack: Kotlin 2.4, AGP 9.4, Gradle 9.7, Room 2.8, Ktor 3.6, Compose BOM 2026.09.

## The KMP library module (AGP 9)

```kotlin
// shared/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)   // com.android.kotlin.multiplatform.library
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
kotlin {
    android { namespace = "…"; compileSdk = …; minSdk = …; withHostTest {} }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
}
```

- **`android { }` inside `kotlin { }`** is the AGP 9 way. The old
  `com.android.library` + `androidTarget()` combination is deprecated.
- **`withHostTest {}`** enables plain JVM unit tests for the Android target
  (`testAndroidHostTest`).
- **No `iosX64`.** Intel simulators are rare now. Add it back only if your
  team still has Intel Macs.
- **Static framework.** Faster app launch and no embedding step. Use a dynamic
  framework only if you need to share it between app extensions.
- **KSP per target.** Room's compiler is added with `add("kspAndroid", …)`,
  `add("kspIosArm64", …)` and `add("kspIosSimulatorArm64", …)`.

## The Android app module (AGP 9)

AGP 9 has Kotlin built in, so [`androidApp/build.gradle.kts`](../androidApp/build.gradle.kts)
applies only `com.android.application` and the Compose compiler plugin, with no
`kotlin-android`. Per-build-type values such as `API_BASE_URL` use
`buildConfigField`, which needs `buildFeatures { buildConfig = true }`.

## Xcode integration

The Xcode project has a **Run Script** build phase that runs before
*Compile Sources*:

```sh
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Gradle builds the framework for whichever architecture and configuration Xcode
is building, and Xcode finds it through
`FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
plus `OTHER_LDFLAGS = -framework Shared`. Required details:

- `ENABLE_USER_SCRIPT_SANDBOXING = NO`, or the script can't run Gradle.
- `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`, because there's no
  `iosX64` target. Without it, "Any iOS Simulator Device" builds fail.
- The project uses **synchronized folders** (Xcode 16+), so new Swift files
  are picked up without editing `project.pbxproj`. This keeps merge conflicts
  to a minimum.
- `Info.plist` sits *outside* the synced folder (`iosApp/Info.plist`);
  otherwise Xcode would also copy it as a resource and fail with "multiple
  commands produce Info.plist".
- Build settings flow into the app through Info.plist variables:
  `API_BASE_URL = $(API_BASE_URL)`.

## Gradle settings

[`gradle.properties`](../gradle.properties) turns on the configuration cache
and the build cache, which makes repeated builds much faster. Every build in
this repo runs with the configuration cache enabled.

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) has two jobs:

| Job | Runner | Runs |
|---|---|---|
| JVM & Android | Ubuntu | shared JVM tests, server tests, Android debug APK |
| iOS | macOS | shared tests on the iOS simulator, Xcode build of the app |

The iOS job matters: Kotlin/Native behaves differently from the JVM (memory
model, exceptions crossing into Swift, Room's native driver). Code that is
only tested on the JVM is not tested.
