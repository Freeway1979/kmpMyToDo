package com.andy.mytodo.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andy.mytodo.data.OfflineFirstTodoRepository
import com.andy.mytodo.data.local.TodoDatabase
import com.andy.mytodo.data.remote.KtorTodoApi
import com.andy.mytodo.data.remote.createHttpClient
import com.andy.mytodo.data.sync.SyncManager
import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.presentation.TodoListViewModel
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Values that differ per build or platform. */
data class AppConfig(
    val apiBaseUrl: String,
    val enableNetworkLogging: Boolean = false,
)

/**
 * App-wide object graph (manual DI). Each platform creates exactly one,
 * passing in only what is truly platform specific: the database builder
 * result, the HTTP engine and the config.
 */
class AppContainer(
    database: TodoDatabase,
    httpEngine: HttpClientEngine,
    config: AppConfig,
) {
    /** Lives as long as the process; work here survives screens coming and going. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dao = database.todoDao()
    private val api = KtorTodoApi(createHttpClient(httpEngine, config.apiBaseUrl, config.enableNetworkLogging))

    val repository: TodoRepository = OfflineFirstTodoRepository(dao)
    val syncManager = SyncManager(dao, api, appScope).also { it.start() }

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { TodoListViewModel(repository, syncManager) }
    }
}
