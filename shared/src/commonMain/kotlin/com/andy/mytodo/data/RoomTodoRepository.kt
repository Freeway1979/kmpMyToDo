package com.andy.mytodo.data

import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.domain.TodoRules
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class RoomTodoRepository(
    private val dao: TodoDao,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TodoRepository {

    override fun observeTodos(): Flow<List<Todo>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun add(title: String, notes: String, priority: Priority): Todo {
        val time = now()
        val entity = TodoEntity(
            title = TodoRules.normalizeTitle(title),
            notes = notes.trim(),
            priority = priority.rank,
            isDone = false,
            createdAt = time,
            updatedAt = time,
            completedAt = null,
        )
        val id = dao.upsert(entity)
        return entity.copy(id = id).toModel()
    }

    override suspend fun update(id: Long, title: String, notes: String, priority: Priority) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                title = TodoRules.normalizeTitle(title),
                notes = notes.trim(),
                priority = priority.rank,
                updatedAt = now(),
            )
        )
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        val time = now()
        dao.setDone(id, done, completedAt = if (done) time else null, now = time)
    }

    override suspend fun delete(id: Long): Todo? {
        val existing = dao.getById(id) ?: return null
        dao.deleteById(id)
        return existing.toModel()
    }

    override suspend fun restore(todo: Todo) {
        dao.upsert(todo.toEntity())
    }

    override suspend fun clearCompleted(): Int = dao.deleteCompleted()
}
