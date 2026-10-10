package com.andy.mytodo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a row stands relative to the server. */
enum class SyncState {
    /** Matches the server's copy. */
    Synced,

    /** Created or edited locally; must be pushed. */
    Dirty,

    /** Deleted locally (a tombstone); hidden from the UI until the server confirms the delete. */
    Deleted,
}

@Entity(tableName = "todos", indices = [Index("syncState")])
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val priority: Int,
    val isDone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    @ColumnInfo(defaultValue = "'Dirty'") val syncState: SyncState = SyncState.Dirty,
)
