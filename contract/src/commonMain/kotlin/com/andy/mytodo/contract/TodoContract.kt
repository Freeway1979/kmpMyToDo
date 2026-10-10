package com.andy.mytodo.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of a to-do. Changing this is an API change: keep new fields
 * optional (with defaults) so old clients and servers keep working.
 */
@Serializable
data class TodoDto(
    /** Client-generated UUID, so items can be created offline without a server round-trip. */
    val id: String,
    val title: String,
    val notes: String = "",
    val priority: PriorityDto = PriorityDto.Normal,
    val isDone: Boolean = false,
    val createdAt: Long,
    /** Epoch millis of the last edit; the server keeps the newest version (last-write-wins). */
    val updatedAt: Long,
    val completedAt: Long? = null,
)

@Serializable
enum class PriorityDto {
    @SerialName("low") Low,
    @SerialName("normal") Normal,
    @SerialName("high") High,
}

/** Paths relative to the API base URL. */
object TodoRoutes {
    const val HEALTH = "health"
    const val TODOS = "api/v1/todos"
    fun todo(id: String) = "$TODOS/$id"
}
