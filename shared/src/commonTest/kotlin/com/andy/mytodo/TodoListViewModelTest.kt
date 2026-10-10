package com.andy.mytodo

import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import com.andy.mytodo.model.TodoFilter
import com.andy.mytodo.presentation.TodoListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTodoRepository : TodoRepository {
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    private var nextId = 1L

    override fun observeTodos(): Flow<List<Todo>> = todos
    override suspend fun add(title: String, notes: String, priority: Priority): Todo {
        val id = nextId++
        return todo(id, title.trim(), priority = priority).also { t -> todos.update { it + t } }
    }
    override suspend fun update(id: Long, title: String, notes: String, priority: Priority) =
        todos.update { list -> list.map { if (it.id == id) it.copy(title = title, notes = notes, priority = priority) else it } }
    override suspend fun setDone(id: Long, done: Boolean) =
        todos.update { list -> list.map { if (it.id == id) it.copy(isDone = done, completedAt = if (done) 1000 else null) else it } }
    override suspend fun delete(id: Long): Todo? =
        todos.value.find { it.id == id }?.also { t -> todos.update { it - t } }
    override suspend fun restore(todo: Todo) = todos.update { it + todo }
    override suspend fun clearCompleted(): Int {
        val n = todos.value.count { it.isDone }
        todos.update { list -> list.filterNot { it.isDone } }
        return n
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun addToggleDeleteUndo() = runTest {
        val repo = FakeTodoRepository()
        val vm = TodoListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onDraftTitleChange("   ")
        assertFalse(vm.addTodo())
        assertNotNull(vm.uiState.value.draftError)
        assertEquals(0, vm.uiState.value.totalCount)

        vm.onDraftTitleChange("Buy milk")
        assertTrue(vm.addTodo())
        val state = vm.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals("", state.draftTitle)
        assertEquals("1 item left", state.summary)

        val item = state.items.single()
        vm.toggleDone(item)
        assertEquals(1, vm.uiState.value.completedCount)
        vm.onFilterChange(TodoFilter.Active)
        assertTrue(vm.uiState.value.items.isEmpty())

        vm.onFilterChange(TodoFilter.All)
        vm.delete(vm.uiState.value.items.single())
        assertEquals(0, vm.uiState.value.totalCount)
        assertNotNull(vm.uiState.value.recentlyDeleted)

        vm.undoDelete()
        assertEquals(1, vm.uiState.value.totalCount)
        assertNull(vm.uiState.value.recentlyDeleted)
    }

    @Test
    fun updateRejectsInvalidTitle() = runTest {
        val repo = FakeTodoRepository()
        val vm = TodoListViewModel(repo)
        val t = repo.add("Original", "", Priority.Normal)
        assertNotNull(vm.updateTodo(t.id, "", "", Priority.High))
        assertNull(vm.updateTodo(t.id, "Renamed", "notes", Priority.High))
        assertEquals("Renamed", repo.todos.value.single().title)
    }
}
