package com.andy.mytodo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    /** Everything the user should see: tombstones are hidden. */
    @Query("SELECT * FROM todos WHERE syncState != 'Deleted'")
    fun observeVisible(): Flow<List<TodoEntity>>

    @Query("SELECT COUNT(*) FROM todos WHERE syncState != 'Synced'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM todos WHERE syncState != 'Synced'")
    suspend fun getPending(): List<TodoEntity>

    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): TodoEntity?

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Upsert
    suspend fun upsertAll(todos: List<TodoEntity>)

    @Query("UPDATE todos SET isDone = :done, completedAt = :completedAt, updatedAt = :now, syncState = 'Dirty' WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, completedAt: Long?, now: Long)

    @Query("UPDATE todos SET syncState = 'Deleted', updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("UPDATE todos SET syncState = 'Deleted', updatedAt = :now WHERE isDone = 1 AND syncState != 'Deleted'")
    suspend fun markCompletedDeleted(now: Long): Int

    /** Removes a tombstone once the server confirmed the delete (unless it was restored meanwhile). */
    @Query("DELETE FROM todos WHERE id = :id AND syncState = 'Deleted'")
    suspend fun purgeTombstone(id: String)

    @Query("DELETE FROM todos WHERE id IN (:ids) AND syncState = 'Synced'")
    suspend fun deleteSynced(ids: List<String>)

    /**
     * Records the server's answer to a push. Only applies when the row is
     * exactly as it was when we sent it; if the user edited it while the
     * request was in flight, the row stays dirty and is pushed again.
     */
    @Transaction
    suspend fun markPushed(sent: TodoEntity, accepted: TodoEntity) {
        val current = getById(sent.id) ?: return
        if (current.syncState == SyncState.Dirty && current.updatedAt == sent.updatedAt) {
            upsert(accepted.copy(syncState = SyncState.Synced))
        }
    }

    /**
     * Merges the server's full list into the local table:
     * server rows win unless the local row has unpushed changes, and rows we
     * had synced that the server no longer has were deleted on another device.
     */
    @Transaction
    suspend fun applyRemoteSnapshot(remote: List<TodoEntity>) {
        val local = getAll().associateBy { it.id }
        val incoming = remote.filter { local[it.id]?.syncState.let { state -> state == null || state == SyncState.Synced } }
        if (incoming.isNotEmpty()) upsertAll(incoming.map { it.copy(syncState = SyncState.Synced) })

        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val goneIds = local.values.filter { it.syncState == SyncState.Synced && it.id !in remoteIds }.map { it.id }
        if (goneIds.isNotEmpty()) deleteSynced(goneIds)
    }
}
