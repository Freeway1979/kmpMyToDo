package com.andy.mytodo.data

import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.local.TodoDao
import com.andy.mytodo.data.local.TodoEntity
import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.domain.TodoRules
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Offline-first: every write goes to the local database immediately and is
 * marked Dirty (or Deleted). The UI only ever reads the database. Pushing
 * changes to the server is the SyncManager's job, not the repository's.
 */
class OfflineFirstTodoRepository(
    private val dao: TodoDao,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { Uuid.random().toString() },
) : TodoRepository {

    override fun observeTodos(): Flow<List<Todo>> =
        dao.observeVisible().map { list -> list.map { it.toModel() } }

    override suspend fun add(title: String, notes: String, priority: Priority): Todo {
        val time = now()
        val entity = TodoEntity(
            id = newId(),
            title = TodoRules.normalizeTitle(title),
            notes = notes.trim(),
            priority = priority.rank,
            isDone = false,
            createdAt = time,
            updatedAt = time,
            completedAt = null,
            syncState = SyncState.Dirty,
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    override suspend fun update(id: String, title: String, notes: String, priority: Priority) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                title = TodoRules.normalizeTitle(title),
                notes = notes.trim(),
                priority = priority.rank,
                updatedAt = now(),
                syncState = SyncState.Dirty,
            )
        )
    }

    override suspend fun setDone(id: String, done: Boolean) {
        val time = now()
        dao.setDone(id, done, completedAt = if (done) time else null, now = time)
    }

    override suspend fun delete(id: String): Todo? {
        val existing = dao.getById(id) ?: return null
        dao.markDeleted(id, now())
        return existing.toModel()
    }

    override suspend fun restore(todo: Todo) {
        // A fresh timestamp makes the restored copy win over the pending delete.
        dao.upsert(todo.copy(updatedAt = now()).toEntity(SyncState.Dirty))
    }

    override suspend fun clearCompleted(): Int = dao.markCompletedDeleted(now())
}
