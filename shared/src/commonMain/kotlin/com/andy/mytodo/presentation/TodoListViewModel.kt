package com.andy.mytodo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andy.mytodo.domain.SyncController
import com.andy.mytodo.domain.SyncStatus
import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.domain.TodoRules
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import com.andy.mytodo.model.TodoFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoListUiState(
    val isLoading: Boolean = true,
    val items: List<Todo> = emptyList(),
    val filter: TodoFilter = TodoFilter.All,
    val query: String = "",
    val draftTitle: String = "",
    val draftPriority: Priority = Priority.Normal,
    val draftError: String? = null,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val summary: String = "",
    val emptyMessage: String = "",
    /** Set right after a delete so the UI can offer "Undo". */
    val recentlyDeleted: Todo? = null,
    // Sync is flattened into plain fields: easier for SwiftUI than a sealed class.
    val syncLabel: String = "",
    val isSyncing: Boolean = false,
    val syncFailed: Boolean = false,
) {
    val canClearCompleted: Boolean get() = completedCount > 0
    val filters: List<TodoFilter> get() = TodoFilter.entries
    val priorities: List<Priority> get() = Priority.entries
}

private data class LocalInputs(
    val filter: TodoFilter = TodoFilter.All,
    val query: String = "",
    val draftTitle: String = "",
    val draftPriority: Priority = Priority.Normal,
    val draftError: String? = null,
    val recentlyDeleted: Todo? = null,
)

class TodoListViewModel(
    private val repository: TodoRepository,
    private val sync: SyncController,
) : ViewModel() {

    private val inputs = MutableStateFlow(LocalInputs())

    val uiState: StateFlow<TodoListUiState> =
        combine(repository.observeTodos(), inputs, sync.status, sync.pendingCount) { todos, input, syncStatus, pending ->
            val active = todos.count { !it.isDone }
            TodoListUiState(
                isLoading = false,
                items = TodoRules.visibleTodos(todos, input.filter, input.query),
                filter = input.filter,
                query = input.query,
                draftTitle = input.draftTitle,
                draftPriority = input.draftPriority,
                draftError = input.draftError,
                activeCount = active,
                completedCount = todos.size - active,
                totalCount = todos.size,
                summary = TodoRules.summary(active, todos.size),
                emptyMessage = TodoRules.emptyMessage(input.filter, input.query, todos.size),
                recentlyDeleted = input.recentlyDeleted,
                syncLabel = TodoRules.syncLabel(syncStatus, pending),
                isSyncing = syncStatus == SyncStatus.Syncing,
                syncFailed = syncStatus is SyncStatus.Failed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListUiState())

    fun onDraftTitleChange(title: String) =
        inputs.update { it.copy(draftTitle = title, draftError = null) }

    fun onDraftPriorityChange(priority: Priority) =
        inputs.update { it.copy(draftPriority = priority) }

    fun onFilterChange(filter: TodoFilter) = inputs.update { it.copy(filter = filter) }

    fun onQueryChange(query: String) = inputs.update { it.copy(query = query) }

    /**
     * Adds the current draft. Returns true when accepted so the UI can clear its
     * text field (text fields own their text locally to avoid async round-trips).
     */
    fun addTodo(): Boolean {
        val draft = inputs.value
        TodoRules.validateTitle(draft.draftTitle)?.let { error ->
            inputs.update { it.copy(draftError = error) }
            return false
        }
        inputs.update { it.copy(draftTitle = "", draftPriority = Priority.Normal, draftError = null) }
        viewModelScope.launch { repository.add(draft.draftTitle, notes = "", priority = draft.draftPriority) }
        return true
    }

    /**
     * Validates and saves an edit. Returns an error message to show in the
     * editor, or null when the change was accepted.
     */
    fun updateTodo(id: String, title: String, notes: String, priority: Priority): String? {
        val error = TodoRules.validateTitle(title) ?: TodoRules.validateNotes(notes)
        if (error == null) viewModelScope.launch { repository.update(id, title, notes, priority) }
        return error
    }

    fun toggleDone(todo: Todo) {
        viewModelScope.launch { repository.setDone(todo.id, !todo.isDone) }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch {
            val deleted = repository.delete(todo.id) ?: return@launch
            inputs.update { it.copy(recentlyDeleted = deleted) }
        }
    }

    fun undoDelete() {
        val deleted = inputs.value.recentlyDeleted ?: return
        inputs.update { it.copy(recentlyDeleted = null) }
        viewModelScope.launch { repository.restore(deleted) }
    }

    fun dismissUndo() = inputs.update { it.copy(recentlyDeleted = null) }

    /**
     * Pull-to-refresh. A suspend function so the UI can keep its spinner up
     * until sync finishes; Swift sees it as `async`.
     */
    suspend fun refresh() {
        sync.sync()
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }
}
