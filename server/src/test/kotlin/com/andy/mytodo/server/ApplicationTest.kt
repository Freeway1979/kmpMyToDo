package com.andy.mytodo.server

import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.contract.TodoRoutes
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    private fun todo(updatedAt: Long, title: String = "Buy milk") =
        TodoDto(id = "a1", title = title, createdAt = 1, updatedAt = updatedAt)

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun upsertListAndDelete() = testApplication {
        application { todoModule() }
        val client = jsonClient()

        val saved = client.put("/${TodoRoutes.todo("a1")}") {
            contentType(ContentType.Application.Json)
            setBody(todo(updatedAt = 10))
        }.body<TodoDto>()
        assertEquals("Buy milk", saved.title)
        assertEquals(listOf(saved), client.get("/${TodoRoutes.TODOS}").body<List<TodoDto>>())

        assertEquals(HttpStatusCode.NoContent, client.delete("/${TodoRoutes.todo("a1")}").status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/${TodoRoutes.todo("a1")}").status) // idempotent
        assertEquals(emptyList(), client.get("/${TodoRoutes.TODOS}").body<List<TodoDto>>())
    }

    @Test
    fun olderWriteLosesToNewer() = testApplication {
        application { todoModule() }
        val client = jsonClient()
        suspend fun put(dto: TodoDto) = client.put("/${TodoRoutes.todo(dto.id)}") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.body<TodoDto>()

        put(todo(updatedAt = 20, title = "Newer"))
        val result = put(todo(updatedAt = 10, title = "Older"))
        assertEquals("Newer", result.title)
    }

    @Test
    fun rejectsMismatchedId() = testApplication {
        application { todoModule() }
        val response = jsonClient().put("/${TodoRoutes.todo("other")}") {
            contentType(ContentType.Application.Json)
            setBody(todo(updatedAt = 1))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun storeSurvivesRestartWhenBackedByFile() {
        val file = File.createTempFile("todos", ".json").apply { delete() }
        try {
            TodoStore(file).upsert(todo(updatedAt = 1, title = "Persisted"))
            assertEquals("Persisted", TodoStore(file).all().single().title)
        } finally {
            file.delete()
        }
    }
}
