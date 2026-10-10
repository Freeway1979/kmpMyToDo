package com.andy.mytodo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andy.mytodo.presentation.TodoListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TodoApplication).container
        setContent {
            TodoTheme {
                val viewModel: TodoListViewModel = viewModel(factory = container.viewModelFactory)
                TodoListScreen(viewModel)
            }
        }
    }
}
