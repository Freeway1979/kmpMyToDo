# 9. Testing

## Where tests live

| Source set | Runs on | Tests |
|---|---|---|
| `shared/src/commonTest` | JVM **and** iOS simulator | [rules](../shared/src/commonTest/kotlin/com/andy/mytodo/TodoRulesTest.kt), [ViewModel](../shared/src/commonTest/kotlin/com/andy/mytodo/TodoListViewModelTest.kt), [sync engine](../shared/src/commonTest/kotlin/com/andy/mytodo/data/SyncManagerTest.kt) |
| `shared/src/iosTest` | iOS simulator | [Room migration](../shared/src/iosTest/kotlin/com/andy/mytodo/MigrationTest.kt) |
| `server/src/test` | JVM | [API and store](../server/src/test/kotlin/com/andy/mytodo/server/ApplicationTest.kt) |

```bash
./gradlew :shared:testAndroidHostTest     # common tests on the JVM
./gradlew :shared:iosSimulatorArm64Test   # common + iOS tests on the simulator
./gradlew :server:test
```

**Run common tests on both.** Kotlin/Native differs from the JVM in
threading, exceptions and the SQLite driver. A test that only runs on the JVM
doesn't prove the iOS app works.

## Fakes over mocking libraries

MockK is JVM-only. Mokkery supports KMP if you really want a mocking library,
but hand-written fakes work everywhere and stay readable:

- [`FakeTodoDao`](../shared/src/commonTest/kotlin/com/andy/mytodo/fakes/FakeTodoDao.kt)
  is an in-memory map mirroring the DAO's SQL. Because `markPushed` and
  `applyRemoteSnapshot` are default methods on the DAO interface, the fake
  **inherits the real merge logic**.
- `FakeTodoRepository` and `FakeSyncController` (in the ViewModel test) are a
  few lines each.

## Test networking with MockEngine

[`FakeServer`](../shared/src/commonTest/kotlin/com/andy/mytodo/fakes/FakeServer.kt)
is a small backend behind Ktor's `MockEngine`. Requests go through the
**real** client setup (`createHttpClient`), serialization, routes and
`KtorTodoApi`; only the socket is fake. It can simulate:

- going offline (`offline = true` throws an `IOException`)
- HTTP errors (`failWithStatus = InternalServerError`)
- the user editing while a request is in flight (`duringPut = { … }`)

## Coroutines in tests

- `runTest` with an `UnconfinedTestDispatcher` set as `Dispatchers.Main`
  (through `setMain`) makes ViewModel updates happen immediately.
- Collect `uiState` in `backgroundScope` so `WhileSubscribed` is active.
- Pass `backgroundScope` as the sync engine's app scope. Its retry loop
  would otherwise keep `runTest` waiting forever.
- Inject the clock and ID generator (`now = { ++clock }`, `newId = { "id-${++ids}" }`)
  for deterministic results.

## A test is only useful if it can fail

When adding a test for tricky code (migrations, sync races), break the code
on purpose once and watch the test go red. The migration test was checked by
deleting the `CREATE INDEX` line, which makes Room reject the schema and the
test fail.

## Then test on devices

Unit tests didn't catch any of the bugs in
[Lessons learned](10-lessons-learned.md). They were found by running the apps:
typing quickly, deleting with the keyboard open, upgrading over existing data,
and stopping the server. For each change, run through it on a real device or
emulator and a simulator, with the placeholder server running.
