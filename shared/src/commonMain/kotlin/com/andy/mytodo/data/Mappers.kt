package com.andy.mytodo.data

import com.andy.mytodo.contract.PriorityDto
import com.andy.mytodo.contract.TodoDto
import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.local.TodoEntity
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo

// Each layer has its own type (entity, DTO, model) so the database schema,
// the wire format and the UI can change independently. Mapping lives here.

internal fun TodoEntity.toModel() = Todo(
    id = id,
    title = title,
    notes = notes,
    priority = Priority.entries.firstOrNull { it.rank == priority } ?: Priority.Normal,
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    isPendingSync = syncState != SyncState.Synced,
)

internal fun Todo.toEntity(syncState: SyncState) = TodoEntity(
    id = id,
    title = title,
    notes = notes,
    priority = priority.rank,
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    syncState = syncState,
)

internal fun TodoEntity.toDto() = TodoDto(
    id = id,
    title = title,
    notes = notes,
    priority = when (priority) {
        Priority.Low.rank -> PriorityDto.Low
        Priority.High.rank -> PriorityDto.High
        else -> PriorityDto.Normal
    },
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

internal fun TodoDto.toEntity() = TodoEntity(
    id = id,
    title = title,
    notes = notes,
    priority = when (priority) {
        PriorityDto.Low -> Priority.Low.rank
        PriorityDto.Normal -> Priority.Normal.rank
        PriorityDto.High -> Priority.High.rank
    },
    isDone = isDone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    syncState = SyncState.Synced,
)
