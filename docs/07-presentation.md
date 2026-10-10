# 7. Presentation: a shared ViewModel

## One ViewModel for both platforms

[`TodoListViewModel`](../shared/src/commonMain/kotlin/com/andy/mytodo/presentation/TodoListViewModel.kt)
extends `androidx.lifecycle.ViewModel`, which has been multiplatform since
lifecycle 2.8. Android uses it the normal way. iOS gets the same
`viewModelScope`, the same `onCleared` and the same factory
([chapter 8](08-ios-interop.md)).

It depends only on `domain` interfaces (`TodoRepository`, `SyncController`),
never on Room or Ktor.

## Unidirectional data flow

```
          intents (onDraftTitleChange, addTodo, toggleDone, delete, refresh…)
   UI  ───────────────────────────────────────────────────────────▶  ViewModel
   UI  ◀───────────────────────────────────────────────────────────  StateFlow<TodoListUiState>
                              one immutable state object
```

`uiState` is built with `combine(todos, localInputs, syncStatus, pendingCount)`
and `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`:

- **One state object** means the UI can't show a mix of old and new values.
- **`WhileSubscribed(5_000)`** stops the upstream flows 5 seconds after the
  last collector leaves, but survives an Android configuration change.

## Put display logic in the state, not the UI

`TodoListUiState` already contains finished values: the filtered and sorted
`items`, `summary` ("3 items left"), `emptyMessage`, `syncLabel`, and
`filters` / `priorities` to render. The platform UI only lays them out, so
Android and iOS can't disagree about behaviour or wording.

**Make the state easy for Swift to use.** Sync status is a sealed class in the
domain, but the UI state flattens it into `syncLabel: String`,
`isSyncing: Boolean` and `syncFailed: Boolean`. Swift can't `switch`
exhaustively over a Kotlin sealed class without extra tooling, but booleans and
strings are trivial.

## Text fields own their own text

**Don't bind a text field directly to state that round-trips through a
ViewModel's `StateFlow`.** The new value comes back on a later frame, and a
fast typist's next keystroke is applied to the stale value. That moves the
cursor and garbles the text: typing "Ship KMP demo" produced "Ship KMemoP" on
a Pixel 9 (see [Lessons learned](10-lessons-learned.md)).

The pattern used here, on both platforms:

```kotlin
// Compose
var draft by rememberSaveable { mutableStateOf("") }
OutlinedTextField(value = draft, onValueChange = { draft = it; viewModel.onDraftTitleChange(it) })
val submit = { if (viewModel.addTodo()) draft = "" }
```

```swift
// SwiftUI
@State private var draft = ""
TextField("…", text: $draft).onChange(of: draft) { _, v in vm.onDraftTitleChange(title: v) }
if vm.addTodo() { draft = "" }
```

The ViewModel still receives every change, so it can validate. `addTodo()`
returns `Bool`, so the UI clears its field only when the item was accepted.

## One-shot events: "Undo"

After a delete, `recentlyDeleted` is set in the state. The UI shows its own
platform affordance (an Android Snackbar, an iOS banner) and then calls
`undoDelete()` or `dismissUndo()`, either of which clears the value. Modelling
the event as state means it survives recomposition and rotation, and it can't
be lost when nothing is collecting.

## Suspend functions for "wait until done"

`refresh()` is a `suspend` function. On Android, pull-to-refresh launches it in
a composition scope and hides its spinner when it returns. In SwiftUI it's
called as `.refreshable { try? await vm.refresh() }`, because Kotlin suspend
functions appear in Swift as `async`. Background syncs don't show the
spinner; only user-initiated ones do.

## Edits use validation from shared code

The edit dialog and sheet keep local state and call
`updateTodo(id, title, notes, priority): String?`, which returns an error
message or `null`. Validation rules exist once, in `TodoRules`.
