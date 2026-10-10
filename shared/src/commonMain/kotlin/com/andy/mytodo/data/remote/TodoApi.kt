package com.andy.mytodo.data.remote

import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.contract.TodoRoutes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** The backend, as the app sees it. Implementations throw on failure; callers decide what that means. */
interface TodoApi {
    suspend fun fetchAll(): List<TodoDto>

    /** Idempotent create-or-update. Returns the version the server kept. */
    suspend fun upsert(todo: TodoDto): TodoDto

    /** Idempotent: deleting a missing item succeeds. */
    suspend fun delete(id: String)
}

class KtorTodoApi(private val client: HttpClient) : TodoApi {
    override suspend fun fetchAll(): List<TodoDto> = client.get(TodoRoutes.TODOS).body()

    override suspend fun upsert(todo: TodoDto): TodoDto =
        client.put(TodoRoutes.todo(todo.id)) {
            contentType(ContentType.Application.Json)
            setBody(todo)
        }.body()

    override suspend fun delete(id: String) {
        client.delete(TodoRoutes.todo(id))
    }
}
