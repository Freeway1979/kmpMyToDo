package com.andy.mytodo.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andy.mytodo.data.RoomTodoRepository
import com.andy.mytodo.data.TodoDatabase
import com.andy.mytodo.domain.TodoRepository
import com.andy.mytodo.presentation.TodoListViewModel

/** App-wide object graph. Each platform creates exactly one, passing its database. */
class AppContainer(database: TodoDatabase) {
    val repository: TodoRepository = RoomTodoRepository(database.todoDao())

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { TodoListViewModel(repository) }
    }
}
