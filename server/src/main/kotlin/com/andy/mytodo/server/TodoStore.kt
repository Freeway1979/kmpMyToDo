package com.andy.mytodo.server

import com.andy.mytodo.contract.TodoDto
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Last-write-wins store, optionally saved to [file] so restarts keep data.
 *
 * Persistence matters even for a placeholder: clients treat "missing on the
 * server" as "deleted on another device", so a server that forgets its data
 * on restart would make every client delete its synced items.
 */
class TodoStore(private val file: File? = null) {
    private val json = Json { prettyPrint = true }
    private val todos: MutableMap<String, TodoDto> =
        file?.takeIf { it.exists() }
            ?.let { json.decodeFromString<List<TodoDto>>(it.readText()).associateByTo(LinkedHashMap()) { t -> t.id } }
            ?: LinkedHashMap()

    @Synchronized
    fun all(): List<TodoDto> = todos.values.sortedBy { it.createdAt }

    /** Stores [incoming] unless the server already has a newer version; returns the winner. */
    @Synchronized
    fun upsert(incoming: TodoDto): TodoDto {
        val current = todos[incoming.id]
        val winner = if (current == null || incoming.updatedAt >= current.updatedAt) incoming else current
        todos[winner.id] = winner
        save()
        return winner
    }

    @Synchronized
    fun delete(id: String) {
        if (todos.remove(id) != null) save()
    }

    private fun save() {
        file ?: return
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(todos.values.toList()))
    }
}
