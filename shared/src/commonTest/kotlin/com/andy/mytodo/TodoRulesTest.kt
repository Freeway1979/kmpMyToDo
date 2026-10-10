package com.andy.mytodo

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
) = Todo(id, title, notes = "", priority, done, createdAt, updatedAt = createdAt, completedAt)

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
        assertEquals(listOf(3L, 4L, 1L, 5L, 2L), ids)
    }

    @Test
    fun filterAndSearch() {
        val todos = listOf(todo(1, "Buy milk"), todo(2, "Walk dog", done = true), todo(3, "Buy bread", done = true))
        assertEquals(listOf(1L), TodoRules.visibleTodos(todos, TodoFilter.Active, "").map { it.id })
        assertEquals(listOf(3L), TodoRules.visibleTodos(todos, TodoFilter.Completed, "buy").map { it.id })
    }

    @Test
    fun summary() {
        assertEquals("Nothing to do yet", TodoRules.summary(0, 0))
        assertEquals("All done!", TodoRules.summary(0, 3))
        assertEquals("1 item left", TodoRules.summary(1, 3))
        assertEquals("2 items left", TodoRules.summary(2, 3))
    }
}
