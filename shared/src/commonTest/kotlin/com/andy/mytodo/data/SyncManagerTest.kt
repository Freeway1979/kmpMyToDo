package com.andy.mytodo.data

import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.sync.SyncManager
import com.andy.mytodo.domain.SyncError
import com.andy.mytodo.domain.SyncStatus
import com.andy.mytodo.fakes.FakeServer
import com.andy.mytodo.fakes.FakeTodoDao
import com.andy.mytodo.model.Priority
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncManagerTest {
    private val dao = FakeTodoDao()
    private val server = FakeServer()
    private var clock = 1_000L
    private var ids = 0
    private val repository = OfflineFirstTodoRepository(dao, now = { ++clock }, newId = { "id-${++ids}" })

    private fun TestScope.syncManager() = SyncManager(dao, server.api, backgroundScope, now = { clock })

    private suspend fun visibleTitles() = repository.observeTodos().first().map { it.title }.sorted()

    @Test
    fun pushesLocalChangesAndMarksThemSynced() = runTest {
        val sync = syncManager()
        repository.add("Buy milk", "", Priority.High)
        assertEquals(1, sync.pendingCount.first())

        assertTrue(sync.sync())

        assertEquals("Buy milk", server.todos.values.single().title)
        assertEquals(SyncState.Synced, dao.getAll().single().syncState)
        assertEquals(0, sync.pendingCount.first())
        assertTrue(sync.status.value is SyncStatus.Synced)
    }

    @Test
    fun pullsServerItemsAndDropsOnesDeletedElsewhere() = runTest {
        val sync = syncManager()
        server.todos["a"] = TodoDto(id = "a", title = "From web", createdAt = 1, updatedAt = 1)
        server.todos["b"] = TodoDto(id = "b", title = "Other device", createdAt = 2, updatedAt = 2)

        sync.sync()
        assertEquals(listOf("From web", "Other device"), visibleTitles())

        server.todos.remove("b") // deleted on another device
        sync.sync()
        assertEquals(listOf("From web"), visibleTitles())
    }

    @Test
    fun deleteIsHiddenImmediatelyAndPurgedAfterServerConfirms() = runTest {
        val sync = syncManager()
        val todo = repository.add("Temp", "", Priority.Normal)
        sync.sync()

        repository.delete(todo.id)
        assertEquals(emptyList(), visibleTitles())
        assertEquals(SyncState.Deleted, dao.getById(todo.id)?.syncState) // tombstone kept until pushed

        sync.sync()
        assertTrue(server.todos.isEmpty())
        assertNull(dao.getById(todo.id))
    }

    @Test
    fun offlineKeepsChangesAndReportsStatus() = runTest {
        val sync = syncManager()
        server.offline = true
        repository.add("Written offline", "", Priority.Normal)

        assertFalse(sync.sync())
        assertEquals(SyncStatus.Failed(SyncError.Offline), sync.status.value)
        assertEquals(SyncState.Dirty, dao.getAll().single().syncState)

        server.offline = false
        assertTrue(sync.sync())
        assertEquals("Written offline", server.todos.values.single().title)
    }

    @Test
    fun serverErrorIsReportedAsServerFailure() = runTest {
        val sync = syncManager()
        server.failWithStatus = HttpStatusCode.InternalServerError
        assertFalse(sync.sync())
        assertEquals(SyncStatus.Failed(SyncError.Server), sync.status.value)
    }

    @Test
    fun unpushedLocalEditIsNotOverwrittenByPull() = runTest {
        val sync = syncManager()
        val todo = repository.add("Original", "", Priority.Normal)
        sync.sync()

        server.offline = true
        repository.update(todo.id, "Edited offline", "", Priority.Normal)
        sync.sync() // fails, edit stays dirty
        server.offline = false
        sync.sync()

        assertEquals(listOf("Edited offline"), visibleTitles())
        assertEquals("Edited offline", server.todos.getValue(todo.id).title)
    }

    @Test
    fun newerServerVersionWins() = runTest {
        val sync = syncManager()
        val todo = repository.add("Local", "", Priority.Normal)
        // Another device edited the same item later than our local write.
        server.todos[todo.id] = TodoDto(id = todo.id, title = "Newer on server", createdAt = 1, updatedAt = clock + 1_000)

        sync.sync()

        assertEquals(listOf("Newer on server"), visibleTitles())
        assertEquals(SyncState.Synced, dao.getById(todo.id)?.syncState)
    }

    @Test
    fun editMadeWhilePushIsInFlightIsNotLost() = runTest {
        val sync = syncManager()
        val todo = repository.add("First", "", Priority.Normal)
        server.duringPut = {
            server.duringPut = null
            repository.update(todo.id, "Second", "", Priority.Normal)
        }

        sync.sync()
        assertEquals(SyncState.Dirty, dao.getById(todo.id)?.syncState) // still needs pushing
        assertEquals(listOf("Second"), visibleTitles())

        sync.sync()
        assertEquals("Second", server.todos.getValue(todo.id).title)
        assertEquals(SyncState.Synced, dao.getById(todo.id)?.syncState)
    }

    @Test
    fun undoAfterSyncedDeleteRecreatesOnServer() = runTest {
        val sync = syncManager()
        val todo = repository.add("Keep me", "", Priority.Normal)
        sync.sync()
        val deleted = repository.delete(todo.id)!!
        sync.sync()
        assertTrue(server.todos.isEmpty())

        repository.restore(deleted)
        sync.sync()

        assertEquals("Keep me", server.todos.getValue(todo.id).title)
        assertEquals(listOf("Keep me"), visibleTitles())
    }
}
