package com.andy.mytodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    val priority: Int,
    val isDone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

internal fun TodoEntity.toModel() = Todo(
    id = id,
    title = title,
    notes = notes,
    priority = Priority.entries.firstOrNull { it.rank == priority } ?: Priority.Normal,
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

internal fun Todo.toEntity() = TodoEntity(
    id = id,
    title = title,
    notes = notes,
    priority = priority.rank,
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)
