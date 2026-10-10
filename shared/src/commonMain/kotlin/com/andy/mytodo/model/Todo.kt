package com.andy.mytodo.model

data class Todo(
    val id: Long,
    val title: String,
    val notes: String,
    val priority: Priority,
    val isDone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

enum class Priority(val label: String, val rank: Int) {
    Low("Low", 0),
    Normal("Normal", 1),
    High("High", 2),
}

enum class TodoFilter(val label: String) {
    All("All"),
    Active("Active"),
    Completed("Completed"),
}
