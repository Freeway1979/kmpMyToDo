package com.andy.mytodo.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Failed(val reason: SyncError) : SyncStatus
}

enum class SyncError { Offline, Server, BadResponse }

/**
 * What the presentation layer needs from sync. Defined in `domain` and
 * implemented in `data` (SyncManager), so the ViewModel never depends on
 * networking code and tests can pass a fake.
 */
interface SyncController {
    val status: StateFlow<SyncStatus>
    val pendingCount: Flow<Int>

    /** Pushes local changes, then pulls the server's list. Returns true on success. */
    suspend fun sync(): Boolean
}
