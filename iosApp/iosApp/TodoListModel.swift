import Foundation
import Shared

/// Bridges the shared Kotlin `TodoListViewModel` into SwiftUI's observation system.
/// All logic lives in the shared module; this class only forwards state and intents.
@MainActor
@Observable
final class TodoListModel {
    private(set) var state: TodoListUiState
    let viewModel: TodoListViewModel

    @ObservationIgnored private let owner: TodoListViewModelOwner
    @ObservationIgnored private var subscription: StateSubscription?

    init() {
        owner = SharedApp.shared.makeTodoListOwner()
        viewModel = owner.viewModel
        state = owner.currentState
        subscription = owner.observeState { [weak self] newState in
            MainActor.assumeIsolated { self?.state = newState }
        }
    }

    isolated deinit {
        subscription?.cancel()
        owner.clear()
    }
}
