# 5. Networking (Ktor)

## Libraries

| Need | Library |
|---|---|
| HTTP client | `ktor-client-core` |
| JSON | `kotlinx-serialization-json` + `ktor-client-content-negotiation` |
| Android engine | `ktor-client-okhttp` |
| iOS engine | `ktor-client-darwin` (NSURLSession: respects ATS, proxies, system certificates) |
| Tests | `ktor-client-mock` |

Retrofit and OkHttp alone can't be used: they're JVM-only.

## One client, configured in common code

[`createHttpClient`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/remote/HttpClientFactory.kt)
takes the engine as a parameter. Everything else is shared:

```kotlin
fun createHttpClient(engine: HttpClientEngine, baseUrl: String, enableLogging: Boolean = false) =
    HttpClient(engine) {
        expectSuccess = true                                       // non-2xx → ResponseException
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 15_000 }
        if (enableLogging) install(Logging) { … }
        defaultRequest { url(baseUrl) }
    }
```

- **`expectSuccess = true`** turns HTTP errors into exceptions. Without it, a
  500 response's HTML body would be parsed as your DTO.
- **`ignoreUnknownKeys = true`** means an older app keeps working when the
  server adds fields.
- **One client per app**, created by the container. Each `HttpClient` owns a
  connection pool, so don't create one per request.
- **Logging only in debug builds**, controlled by `AppConfig.enableNetworkLogging`.

## Share the API contract with the server

[`contract/`](../contract/src/commonMain/kotlin/com/andy/mytodo/contract/TodoContract.kt)
holds `TodoDto` and `TodoRoutes`. Both the app's
[`KtorTodoApi`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/remote/TodoApi.kt)
and the [server](../server/src/main/kotlin/com/andy/mytodo/server/Application.kt)
compile against it.

Rules for the contract:

- **Make new fields optional, with defaults.** Old apps keep running after
  a server update, and the reverse.
- **Version the paths** (`api/v1/...`) so a breaking change can live next to
  the old API.
- **Use `@SerialName` for enum values** so a Kotlin rename doesn't change the
  wire format.

## API design that makes sync safe

| Endpoint | Property | Why it matters |
|---|---|---|
| `PUT /api/v1/todos/{id}` | idempotent upsert with a client ID | a retry after a timeout can't create duplicates |
| `DELETE /api/v1/todos/{id}` | idempotent; deleting a missing item succeeds | retries are safe |
| `PUT` response | returns the version the server *kept* | the client learns when a newer copy won ([chapter 6](06-offline-sync.md)) |

## Errors

[`TodoApi`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/remote/TodoApi.kt)
throws, and the caller decides what an error means.
[`SyncManager`](../shared/src/commonMain/kotlin/com/andy/mytodo/data/sync/SyncManager.kt)
maps exceptions to a small enum the UI can understand:

```kotlin
is ResponseException      -> SyncError.Server        // reached the server; it said no
is SerializationException -> SyncError.BadResponse   // server sent something unexpected
else                      -> SyncError.Offline       // couldn't reach it
```

Connectivity failures arrive as different exception types per engine
(`IOException`, `UnresolvedAddressException`, `DarwinHttpRequestException`,
and others), so "everything else is offline" is more reliable than listing
them all.

Always re-throw `CancellationException` before a catch-all. Swallowing it
leaves cancelled coroutines running.

**Never let a Kotlin exception reach Swift.** An exception thrown across into
Swift crashes the app unless the function is marked `@Throws`. Here, network
calls never leave shared code: Swift only sees state.

## Local development: base URL and plain HTTP

| | Android | iOS |
|---|---|---|
| Base URL | `buildConfigField("API_BASE_URL")` per build type | `API_BASE_URL` build setting → Info.plist |
| Reaching the Mac | `adb reverse tcp:8080 tcp:8080`, then `localhost` works on devices *and* emulators | the simulator shares the Mac's `localhost` |
| Allowing `http://` | [`src/debug/res/xml/network_security_config.xml`](../androidApp/src/debug/res/xml/network_security_config.xml) permits only `localhost` and `10.0.2.2`, debug builds only | `NSAllowsLocalNetworking` in [`Info.plist`](../iosApp/Info.plist) |

The release network security config ([`src/main/...`](../androidApp/src/main/res/xml/network_security_config.xml))
allows HTTPS only. The debug file overrides it through the `debug` source set,
so plain HTTP can't accidentally ship in a release build.

Don't forget `<uses-permission android:name="android.permission.INTERNET"/>`.
Without it, every request fails with a permission error (`SocketException: EPERM` or `SecurityException`, depending on the Android version and engine).
