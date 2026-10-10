package com.andy.mytodo

import com.andy.mytodo.domain.SyncError
import com.andy.mytodo.domain.SyncStatus
import com.andy.mytodo.domain.TodoRules
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import com.andy.mytodo.model.TodoFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal fun todo(
    id: Long,
    title: String = "Todo $id",
    done: Boolean = false,
    priority: Priority = Priority.Normal,
    createdAt: Long = id,
    completedAt: Long? = if (done) 100 + id else null,
) = Todo("$id", title, notes = "", priority, done, createdAt, updatedAt = createdAt, completedAt)

class TodoRulesTest {
    @Test
    fun titleValidation() {
        assertNotNull(TodoRules.validateTitle("   "))
        assertNotNull(TodoRules.validateTitle("x".repeat(TodoRules.MAX_TITLE_LENGTH + 1)))
        assertNull(TodoRules.validateTitle("  Buy milk "))
        assertEquals("Buy  oat milk".let(TodoRules::normalizeTitle), "Buy oat milk")
    }

    @Test
    fun activeSortedByPriorityThenNewestCompletedLast() {
        val todos = listOf(
            todo(1, priority = Priority.Low),
            todo(2, done = true),
            todo(3, priority = Priority.High),
            todo(4),
            todo(5, done = true),
        )
        val ids = TodoRules.visibleTodos(todos, TodoFilter.All, "").map { it.id }
        assertEquals(listOf("3", "4", "1", "5", "2"), ids)
    }

    @Test
    fun filterAndSearch() {
        val todos = listOf(todo(1, "Buy milk"), todo(2, "Walk dog", done = true), todo(3, "Buy bread", done = true))
        assertEquals(listOf("1"), TodoRules.visibleTodos(todos, TodoFilter.Active, "").map { it.id })
        assertEquals(listOf("3"), TodoRules.visibleTodos(todos, TodoFilter.Completed, "buy").map { it.id })
    }

    @Test
    fun summary() {
        assertEquals("Nothing to do yet", TodoRules.summary(0, 0))
        assertEquals("All done!", TodoRules.summary(0, 3))
        assertEquals("1 item left", TodoRules.summary(1, 3))
        assertEquals("2 items left", TodoRules.summary(2, 3))
    }

    @Test
    fun syncLabel() {
        assertEquals("Not synced yet", TodoRules.syncLabel(SyncStatus.Idle, 0))
        assertEquals("Syncing…", TodoRules.syncLabel(SyncStatus.Syncing, 3))
        assertEquals("Synced", TodoRules.syncLabel(SyncStatus.Synced(1), 0))
        assertEquals("1 change not synced", TodoRules.syncLabel(SyncStatus.Synced(1), 1))
        assertEquals("Offline · 2 changes not synced", TodoRules.syncLabel(SyncStatus.Failed(SyncError.Offline), 2))
        assertEquals("Offline", TodoRules.syncLabel(SyncStatus.Failed(SyncError.Offline), 0))
        assertEquals("Sync failed, will retry", TodoRules.syncLabel(SyncStatus.Failed(SyncError.Server), 0))
    }
}
