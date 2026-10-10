package com.andy.mytodo.fakes

import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.local.TodoDao
import com.andy.mytodo.data.local.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory TodoDao mirroring the SQL in the real DAO. The @Transaction
 * default methods (markPushed, applyRemoteSnapshot) are inherited, so tests
 * exercise the real merge logic.
 */
class FakeTodoDao : TodoDao {
    val rows = MutableStateFlow<Map<String, TodoEntity>>(emptyMap())

    override fun observeVisible(): Flow<List<TodoEntity>> =
        rows.map { m -> m.values.filter { it.syncState != SyncState.Deleted } }

    override fun observePendingCount(): Flow<Int> =
        rows.map { m -> m.values.count { it.syncState != SyncState.Synced } }

    override suspend fun getPending() = rows.value.values.filter { it.syncState != SyncState.Synced }
    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun getById(id: String) = rows.value[id]
    override suspend fun upsert(todo: TodoEntity) = rows.update { it + (todo.id to todo) }
    override suspend fun upsertAll(todos: List<TodoEntity>) = rows.update { it + todos.associateBy { t -> t.id } }

    override suspend fun setDone(id: String, done: Boolean, completedAt: Long?, now: Long) = edit(id) {
        it.copy(isDone = done, completedAt = completedAt, updatedAt = now, syncState = SyncState.Dirty)
    }

    override suspend fun markDeleted(id: String, now: Long) = edit(id) {
        it.copy(syncState = SyncState.Deleted, updatedAt = now)
    }

    override suspend fun markCompletedDeleted(now: Long): Int {
        val ids = rows.value.values.filter { it.isDone && it.syncState != SyncState.Deleted }.map { it.id }
        ids.forEach { markDeleted(it, now) }
        return ids.size
    }

    override suspend fun purgeTombstone(id: String) =
        rows.update { m -> if (m[id]?.syncState == SyncState.Deleted) m - id else m }

    override suspend fun deleteSynced(ids: List<String>) =
        rows.update { m -> m.filterNot { (id, row) -> id in ids && row.syncState == SyncState.Synced } }

    private fun edit(id: String, change: (TodoEntity) -> TodoEntity) =
        rows.update { m -> m[id]?.let { m + (id to change(it)) } ?: m }
}
