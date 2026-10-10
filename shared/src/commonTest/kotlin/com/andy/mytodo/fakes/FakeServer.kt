package com.andy.mytodo.fakes

import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.contract.TodoRoutes
import com.andy.mytodo.data.remote.KtorTodoApi
import com.andy.mytodo.data.remote.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

/**
 * A fake backend behind Ktor's MockEngine. Requests go through the real
 * HttpClient configuration, serialization and routes; only the socket is fake.
 * Mirrors the server's rules: idempotent PUT/DELETE, last-write-wins.
 */
class FakeServer {
    val todos = mutableMapOf<String, TodoDto>()
    var offline = false
    var failWithStatus: HttpStatusCode? = null

    /** Runs while a PUT is "in flight", to simulate the user editing at the same time. */
    var duringPut: (suspend () -> Unit)? = null

    val requests = mutableListOf<String>()

    private val json = Json { ignoreUnknownKeys = true }

    val engine = MockEngine { request ->
        if (offline) throw IOException("No network")
        val path = request.url.encodedPath.trimStart('/')
        requests += "${request.method.value} $path"
        failWithStatus?.let { return@MockEngine respond("", it) }

        when {
            request.method == HttpMethod.Get && path == TodoRoutes.TODOS ->
                respondJson(json.encodeToString(todos.values.toList()))

            request.method == HttpMethod.Put && path.startsWith(TodoRoutes.TODOS + "/") -> {
                duringPut?.invoke()
                val incoming = json.decodeFromString<TodoDto>(request.body.toByteArray().decodeToString())
                val current = todos[incoming.id]
                val winner = if (current == null || incoming.updatedAt >= current.updatedAt) incoming else current
                todos[winner.id] = winner
                respondJson(json.encodeToString(winner))
            }

            request.method == HttpMethod.Delete && path.startsWith(TodoRoutes.TODOS + "/") -> {
                todos.remove(path.substringAfterLast('/'))
                respond("", HttpStatusCode.NoContent)
            }

            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    val api = KtorTodoApi(createHttpClient(engine, baseUrl = "https://example.test/"))

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
}
