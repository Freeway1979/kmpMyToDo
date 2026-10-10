package com.andy.mytodo.data.sync

import com.andy.mytodo.data.local.SyncState
import com.andy.mytodo.data.local.TodoDao
import com.andy.mytodo.data.remote.TodoApi
import com.andy.mytodo.data.toDto
import com.andy.mytodo.data.toEntity
import com.andy.mytodo.domain.SyncController
import com.andy.mytodo.domain.SyncError
import com.andy.mytodo.domain.SyncStatus
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/**
 * Keeps the local database and the server in step.
 *
 * - **Push, then pull.** Local changes go up first so the pull can't overwrite them.
 * - **Triggers:** app start, shortly after any local change (debounced), manual refresh,
 *   and a backoff retry after failures.
 * - **Runs in [scope]** (app-wide), so leaving a screen doesn't cancel a sync halfway.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    private val dao: TodoDao,
    private val api: TodoApi,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val changeDebounceMillis: Long = 1_000,
    private val initialRetryMillis: Long = 5_000,
    private val maxRetryMillis: Long = 5 * 60_000,
) : SyncController {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()
    override val pendingCount: Flow<Int> = dao.observePendingCount()

    private val mutex = Mutex()
    private var retryJob: Job? = null
    private var retryDelay = initialRetryMillis

    /** Starts automatic syncing. Call once, from the app's composition root. */
    fun start() {
        scope.launch { sync() }
        scope.launch {
            dao.observePendingCount()
                .filter { it > 0 }
                .debounce(changeDebounceMillis)
                .collect { sync() }
        }
    }

    // The work runs in the app scope; callers only wait for it. Cancelling a
    // pull-to-refresh therefore never leaves a half-applied sync behind.
    override suspend fun sync(): Boolean = scope.async { syncOnce() }.await()

    private suspend fun syncOnce(): Boolean = mutex.withLock {
        retryJob?.cancel()
        _status.value = SyncStatus.Syncing
        try {
            push()
            dao.applyRemoteSnapshot(api.fetchAll().map { it.toEntity() })
            _status.value = SyncStatus.Synced(now())
            retryDelay = initialRetryMillis
            true
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: Exception) {
            _status.value = SyncStatus.Failed(e.toSyncError())
            scheduleRetry()
            false
        }
    }

    private suspend fun push() {
        for (entity in dao.getPending()) {
            when (entity.syncState) {
                SyncState.Dirty -> {
                    val accepted = api.upsert(entity.toDto())
                    dao.markPushed(sent = entity, accepted = accepted.toEntity())
                }
                SyncState.Deleted -> {
                    api.delete(entity.id)
                    dao.purgeTombstone(entity.id)
                }
                SyncState.Synced -> Unit
            }
        }
    }

    private fun scheduleRetry() {
        val wait = retryDelay
        retryDelay = (retryDelay * 2).coerceAtMost(maxRetryMillis)
        retryJob = scope.launch {
            delay(wait)
            sync()
        }
    }

    private fun Exception.toSyncError(): SyncError = when (this) {
        is ResponseException -> SyncError.Server
        is SerializationException -> SyncError.BadResponse
        // Engines report connectivity problems with different exception types
        // (IOException, UnresolvedAddressException, DarwinHttpRequestException…).
        else -> SyncError.Offline
    }
}
