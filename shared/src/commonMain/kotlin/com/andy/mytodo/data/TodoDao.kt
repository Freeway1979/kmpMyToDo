package com.andy.mytodo.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Upsert
    suspend fun upsert(todo: TodoEntity): Long

    @Query("UPDATE todos SET isDone = :done, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?, now: Long)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todos WHERE isDone = 1")
    suspend fun deleteCompleted(): Int
}
