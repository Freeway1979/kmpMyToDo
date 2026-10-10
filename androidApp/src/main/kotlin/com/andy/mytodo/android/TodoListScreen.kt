package com.andy.mytodo.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andy.mytodo.domain.TodoRules
import com.andy.mytodo.model.Priority
import com.andy.mytodo.model.Todo
import com.andy.mytodo.presentation.TodoListUiState
import com.andy.mytodo.presentation.TodoListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(viewModel: TodoListViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Todo?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    // Only user-initiated refreshes show the spinner; background syncs don't.
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state.recentlyDeleted) {
        val deleted = state.recentlyDeleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Deleted \"${deleted.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.dismissUndo()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My To-Do", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.summary} · ${state.syncLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.syncFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearCompleted, enabled = state.canClearCompleted) {
                        Text("Clear done")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // imePadding keeps the Undo snackbar above the keyboard instead of behind it.
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // keep the list scrollable above the keyboard
                .fillMaxSize(),
        ) {
            AddTodoBar(state, viewModel)
            SearchField(viewModel::onQueryChange)
            FilterRow(state, viewModel)
            HorizontalDivider()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        viewModel.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.items.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                state.emptyMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp),
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { viewModel.toggleDone(todo) },
                            onDelete = { viewModel.delete(todo) },
                            onClick = { editing = todo },
                            modifier = Modifier.animateItem(),
                        )
                        HorizontalDivider(Modifier.padding(start = 64.dp))
                    }
                }
            }
        }
    }

    editing?.let { todo ->
        EditTodoDialog(
            todo = todo,
            priorities = state.priorities,
            onSave = { title, notes, priority -> viewModel.updateTodo(todo.id, title, notes, priority) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AddTodoBar(state: TodoListUiState, viewModel: TodoListViewModel) {
    // The field owns its text; the view model is told about every change.
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = { if (viewModel.addTodo()) draft = "" }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; viewModel.onDraftTitleChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What needs to be done?") },
                singleLine = true,
                isError = state.draftError != null,
                supportingText = state.draftError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = submit, enabled = TodoRules.validateTitle(draft) == null) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Priority", style = MaterialTheme.typography.labelMedium)
            state.priorities.forEach { priority ->
                FilterChip(
                    selected = state.draftPriority == priority,
                    onClick = { viewModel.onDraftPriorityChange(priority) },
                    label = { Text(priority.label) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(onQueryChange: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val onChange = { text: String -> query = text; onQueryChange(text) }
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun FilterRow(state: TodoListUiState, viewModel: TodoListViewModel) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
        state.filters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = state.filter == filter,
                onClick = { viewModel.onFilterChange(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, state.filters.size),
            ) { Text(filter.label) }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                todo.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (todo.notes.isNotBlank()) {
                Text(
                    todo.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (todo.isPendingSync) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Waiting to sync",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 8.dp).size(16.dp),
            )
        }
        if (!todo.isDone && todo.priority != Priority.Normal) PriorityDot(todo.priority)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriorityDot(priority: Priority) {
    val color = if (priority == Priority.High) MaterialTheme.colorScheme.error else Color(0xFF8E8E93)
    Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
}

@Composable
private fun EditTodoDialog(
    todo: Todo,
    priorities: List<Priority>,
    onSave: (String, String, Priority) -> String?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(todo.id) { mutableStateOf(todo.title) }
    var notes by rememberSaveable(todo.id) { mutableStateOf(todo.notes) }
    var priority by rememberSaveable(todo.id) { mutableStateOf(todo.priority) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit to-do") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Title") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    priorities.forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(title, notes, priority)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
