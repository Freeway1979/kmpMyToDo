package com.andy.mytodo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import com.andy.mytodo.data.TodoDatabase
import com.andy.mytodo.data.buildTodoDatabase
import com.andy.mytodo.di.AppContainer
import com.andy.mytodo.presentation.TodoListUiState
import com.andy.mytodo.presentation.TodoListViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Entry point for Swift: `SharedApp.shared.makeTodoListOwner()`. */
object SharedApp {
    private val container: AppContainer by lazy {
        val builder = Room.databaseBuilder<TodoDatabase>(name = documentDirectory() + "/" + TodoDatabase.FILE_NAME)
        AppContainer(buildTodoDatabase(builder))
    }

    fun makeTodoListOwner(): TodoListViewModelOwner = TodoListViewModelOwner(container)

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val url = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        return requireNotNull(url?.path) { "Documents directory unavailable" }
    }
}

fun interface StateSubscription {
    fun cancel()
}

/**
 * Owns a [TodoListViewModel] for SwiftUI, playing the role an Activity plays on
 * Android. Call [clear] when the owning Swift object goes away.
 */
class TodoListViewModelOwner internal constructor(container: AppContainer) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val viewModel: TodoListViewModel =
        ViewModelProvider.create(this, container.viewModelFactory)[TodoListViewModel::class]

    val currentState: TodoListUiState get() = viewModel.uiState.value

    /** Delivers every UI state on the main thread until cancelled. */
    fun observeState(onEach: (TodoListUiState) -> Unit): StateSubscription {
        val scope = MainScope()
        scope.launch { viewModel.uiState.collect { onEach(it) } }
        return StateSubscription { scope.cancel() }
    }

    fun clear() = viewModelStore.clear()
}
