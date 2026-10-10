package com.andy.mytodo.domain

import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeTodos(): Flow<List<Todo>>
    suspend fun add(title: String, notes: String, priority: Priority): Todo
    suspend fun update(id: String, title: String, notes: String, priority: Priority)
    suspend fun setDone(id: String, done: Boolean)
    /** Deletes the to-do and returns it so it can be restored. */
    suspend fun delete(id: String): Todo?
    suspend fun restore(todo: Todo)
    suspend fun clearCompleted(): Int
}
