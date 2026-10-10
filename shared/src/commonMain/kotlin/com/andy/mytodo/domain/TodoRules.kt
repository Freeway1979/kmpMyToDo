package com.andy.mytodo.domain

import com.andy.mytodo.model.Todo
import com.andy.mytodo.model.TodoFilter

/** Pure business rules shared by every platform. */
object TodoRules {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_NOTES_LENGTH = 2000

    private val whitespace = Regex("\\s+")

    fun normalizeTitle(raw: String): String = raw.trim().replace(whitespace, " ")

    /** Returns an error message, or null when the title is valid. */
    fun validateTitle(raw: String): String? {
        val title = normalizeTitle(raw)
        return when {
            title.isEmpty() -> "Title can't be empty"
            title.length > MAX_TITLE_LENGTH -> "Title must be at most $MAX_TITLE_LENGTH characters"
            else -> null
        }
    }

    fun validateNotes(raw: String): String? =
        if (raw.trim().length > MAX_NOTES_LENGTH) "Notes must be at most $MAX_NOTES_LENGTH characters" else null

    /** Active first (high priority, then newest), completed last (most recently completed first). */
    val displayOrder: Comparator<Todo> = compareBy<Todo> { it.isDone }
        .thenByDescending { if (it.isDone) 0 else it.priority.rank }
        .thenByDescending { if (it.isDone) it.completedAt ?: it.updatedAt else it.createdAt }
        .thenByDescending { it.id }

    fun visibleTodos(todos: List<Todo>, filter: TodoFilter, query: String): List<Todo> {
        val q = query.trim()
        return todos
            .asSequence()
            .filter {
                when (filter) {
                    TodoFilter.All -> true
                    TodoFilter.Active -> !it.isDone
                    TodoFilter.Completed -> it.isDone
                }
            }
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true) }
            .sortedWith(displayOrder)
            .toList()
    }

    fun summary(activeCount: Int, totalCount: Int): String = when {
        totalCount == 0 -> "Nothing to do yet"
        activeCount == 0 -> "All done!"
        activeCount == 1 -> "1 item left"
        else -> "$activeCount items left"
    }

    fun emptyMessage(filter: TodoFilter, query: String, totalCount: Int): String = when {
        query.isNotBlank() -> "No to-dos match \"${query.trim()}\""
        totalCount == 0 -> "Add your first to-do above"
        filter == TodoFilter.Active -> "Nothing left to do"
        filter == TodoFilter.Completed -> "No completed to-dos yet"
        else -> "No to-dos"
    }

    /** One short line describing sync state, shown next to the summary on both platforms. */
    fun syncLabel(status: SyncStatus, pendingChanges: Int): String {
        val pending = when (pendingChanges) {
            0 -> null
            1 -> "1 change not synced"
            else -> "$pendingChanges changes not synced"
        }
        return when (status) {
            SyncStatus.Syncing -> "Syncing…"
            is SyncStatus.Failed -> when (status.reason) {
                SyncError.Offline -> listOfNotNull("Offline", pending).joinToString(" · ")
                SyncError.Server, SyncError.BadResponse -> "Sync failed, will retry"
            }
            is SyncStatus.Synced -> pending ?: "Synced"
            SyncStatus.Idle -> pending ?: "Not synced yet"
        }
    }
}
