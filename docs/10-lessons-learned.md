# 10. Lessons learned

Real problems hit while building this app, in the order they were found. Each
one passed the unit tests at the time.

### Typed text came out scrambled
**Symptom:** typing "Ship KMP demo" quickly on a Pixel 9 saved "Ship KMemoP".
**Cause:** the text field's value was bound to `uiState.draftTitle`, which
reaches the UI a frame later through `combine` + `StateFlow`. Keystrokes were
applied to stale values.
**Fix:** text fields own their text locally and report changes to the
ViewModel. The same latent bug existed in the SwiftUI code and was fixed there
too. → [chapter 7](07-presentation.md#text-fields-own-their-own-text)

### The Undo snackbar was hidden behind the keyboard
**Symptom:** after deleting while the keyboard was open, Undo couldn't be
tapped.
**Cause:** with edge-to-edge, `Scaffold` doesn't pad its snackbar host for the
keyboard (IME) by default.
**Fix:** `SnackbarHost(…, Modifier.imePadding())`, plus `consumeWindowInsets`
and `imePadding()` on the content so the list also stays above the keyboard.

### The latest Compose needed compileSdk 37
**Symptom:** `checkDebugAarMetadata` failed with 13 issues.
**Fix:** raise `androidCompileSdk` in the version catalog. AGP downloads the
platform automatically when the SDK licenses are accepted. `targetSdk` can
stay where it is.

### "Xcode Requested Architecture Not Configured in Gradle"
**Symptom:** building for "Any iOS Simulator Device" (or in CI with
`generic/platform=iOS Simulator`) failed, although building for a specific
simulator worked.
**Cause:** a generic simulator build asks for arm64 **and** x86_64, but the
project deliberately has no `iosX64` target.
**Fix:** `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` in the app target.
It was found by running the exact CI command locally before pushing.

### Kotlin default arguments don't reach Swift
**Symptom:** `TodoListUiState()` didn't compile in Swift.
**Fix:** expose `owner.currentState` instead. Don't rely on Kotlin default
arguments in any API that Swift calls.

### Name clash with Combine
A Kotlin `fun interface Cancellable` would collide with Combine's
`Cancellable` in Swift files that import both. It was renamed to
`StateSubscription`.

### iOS 26 toolbar truncated the summary
A leading toolbar item on iOS 26 renders as a small glass capsule and showed
"No…". The status moved to the list section header.

### The `domain` layer was importing from `data`
The sync status types were first written next to `SyncManager` (in `data`),
so `TodoRules` in `domain` had to import from `data`. That broke the
dependency rule, and the compiler also reported a confusing "ambiguous import"
error. The fix was to move `SyncStatus` and `SyncController` into `domain`.

### An in-memory server would have wiped every client
The placeholder server first kept data in memory. Because sync treats "missing
on the server" as "deleted elsewhere", restarting it would have made every
device delete its synced items. The server now saves to a file. →
[chapter 6](06-offline-sync.md#a-hazard-to-know-about-server-data-loss-spreads)

### Testing pitfalls
- **"Offline" that wasn't offline.** Removing `adb reverse` didn't break the
  app's connection, because OkHttp kept reusing the open connection. To test
  offline, stop the server.
- **Check timestamps before calling something a bug.** A to-do that seemed to
  lose its "done" state during migration had actually been changed an hour
  earlier, which its `updatedAt` showed. The migration test now asserts
  `isDone` explicitly anyway.
- **`adb` against a physical phone.** "Pixel 9 - 15" in Gradle's output was a
  real phone, not an emulator. Use `ANDROID_SERIAL=emulator-5554` (or
  `adb -s`) when automating, so tests never act on someone's personal device.
