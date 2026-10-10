# 8. iOS interop

Kotlin compiles to an Objective-C framework (`Shared.framework`), which Swift
imports with `import Shared`. Most of the interop work is keeping the Swift
side small and natural.

## Give Swift a single entry point

[`SharedApp`](../shared/src/iosMain/kotlin/com/andy/mytodo/IosApp.kt) is a
Kotlin `object`, which Swift sees as `SharedApp.shared`. It lazily builds the
`AppContainer` with the iOS database path, the Darwin engine, and config read
from Info.plist. Swift never assembles Kotlin objects itself.

## Own the ViewModel like an Activity would

`TodoListViewModelOwner` implements `ViewModelStoreOwner`:

```kotlin
class TodoListViewModelOwner internal constructor(container: AppContainer) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val viewModel = ViewModelProvider.create(this, container.viewModelFactory)[TodoListViewModel::class]
    val currentState: TodoListUiState get() = viewModel.uiState.value
    fun observeState(onEach: (TodoListUiState) -> Unit): StateSubscription { … }   // collects on Main
    fun clear() = viewModelStore.clear()                                         // → onCleared(), cancels viewModelScope
}
```

The Android `ViewModelProvider` and factory are reused as-is, and clearing the
store gives iOS the same cleanup Android gets.

## Observe StateFlow from SwiftUI

[`TodoListModel.swift`](../iosApp/iosApp/TodoListModel.swift) is the only
bridge class:

```swift
@MainActor @Observable
final class TodoListModel {
    private(set) var state: TodoListUiState
    let viewModel: TodoListViewModel
    init() {
        owner = SharedApp.shared.makeTodoListOwner()
        state = owner.currentState
        subscription = owner.observeState { [weak self] s in MainActor.assumeIsolated { self?.state = s } }
    }
    isolated deinit { subscription?.cancel(); owner.clear() }
}
```

- The Kotlin side collects on `MainScope()`, so callbacks arrive on the main
  thread and `MainActor.assumeIsolated` is correct.
- The returned handle is named `StateSubscription`, not `Cancellable`,
  because Swift code using Combine already has a `Cancellable` type and the
  names would clash.

**Alternative: SKIE.** The [SKIE](https://skie.touchlab.co) compiler plugin
exposes `Flow` as Swift `AsyncSequence`, sealed classes as Swift enums, and
default arguments as overloads. It's worth adopting when the bridge grows past
a few classes. Check that it supports your Kotlin version first: it often
trails new Kotlin releases.

## What Kotlin looks like from Swift

| Kotlin | Swift | Watch out for |
|---|---|---|
| `object SharedApp` | `SharedApp.shared` | |
| `enum class Priority { High }` | `Priority.high` | it's a class, so `switch` needs a `default` |
| `data class` with default arguments | an initializer with **all** parameters | default values aren't exported; expose `currentState` or a factory instead |
| `List<Todo>` | `[Todo]` | |
| `Long`, `Int` | `Int64`, `Int32` | |
| `String?` | `String?` | |
| `suspend fun refresh()` | `func refresh() async throws` | call with `try? await` |
| `fun addTodo(): Boolean` | `func addTodo() -> Bool` | |
| `(T) -> Unit` | Swift closure | |
| an exception thrown into Swift | **crash** | catch in Kotlin, or mark with `@Throws` |

Kotlin classes are `NSObject` subclasses, so they're `Hashable` and
`Equatable` (through `isEqual`) and work in `ForEach(id: \.self)` and
`Picker` tags. To use one with `.sheet(item:)`, add
`extension Todo: @retroactive Identifiable {}`; `id` already exists.

## Keep it in shared code, not Swift

Every time Swift needs logic, put it in Kotlin and expose the result. Examples
from this app:

- `TodoRules.shared.validateTitle(raw:)` decides whether the Add button is enabled.
- `state.syncLabel` is the status text, already formatted.
- `addTodo()` returns whether the item was accepted.
