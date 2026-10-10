package com.andy.mytodo.server

import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.contract.TodoRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.io.File

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val dataFile = File(System.getenv("TODO_DATA_FILE") ?: "build/data/todos.json")
    println("Serving on http://0.0.0.0:$port, data in ${dataFile.absolutePath}")
    embeddedServer(Netty, port = port, host = "0.0.0.0") { todoModule(TodoStore(dataFile)) }.start(wait = true)
}

fun Application.todoModule(store: TodoStore = TodoStore()) {
    install(ContentNegotiation) { json() }
    install(CallLogging)

    routing {
        get("/${TodoRoutes.HEALTH}") { call.respondText("ok") }

        get("/${TodoRoutes.TODOS}") { call.respond(store.all()) }

        // PUT is an idempotent upsert keyed by the client-generated id, so a
        // client can safely retry after a timeout without creating duplicates.
        put("/${TodoRoutes.TODOS}/{id}") {
            val id = call.parameters["id"]!!
            val todo = call.receive<TodoDto>()
            if (todo.id != id) {
                call.respond(HttpStatusCode.BadRequest, "Body id does not match path id")
                return@put
            }
            call.respond(store.upsert(todo))
        }

        // Idempotent: deleting a missing item still succeeds.
        delete("/${TodoRoutes.TODOS}/{id}") {
            store.delete(call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
