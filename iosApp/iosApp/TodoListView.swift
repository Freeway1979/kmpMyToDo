import SwiftUI
import Shared

struct TodoListView: View {
    let model: TodoListModel

    @State private var draft = ""
    @State private var query = ""
    @State private var editing: Todo?
    @FocusState private var draftFocused: Bool

    private var state: TodoListUiState { model.state }
    private var vm: TodoListViewModel { model.viewModel }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    addRow
                    Picker("Filter", selection: Binding(get: { state.filter }, set: { vm.onFilterChange(filter: $0) })) {
                        ForEach(state.filters, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowSeparator(.hidden)
                }

                Section {
                    if state.items.isEmpty && !state.isLoading {
                        Text(state.emptyMessage)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 24)
                            .listRowBackground(Color.clear)
                    }
                    ForEach(state.items, id: \.id) { todo in
                        TodoRow(todo: todo) { vm.toggleDone(todo: todo) }
                            .contentShape(Rectangle())
                            .onTapGesture { editing = todo }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) { vm.delete(todo: todo) } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                            .swipeActions(edge: .leading) {
                                Button { vm.toggleDone(todo: todo) } label: {
                                    Label(todo.isDone ? "Undo" : "Done",
                                          systemImage: todo.isDone ? "arrow.uturn.backward" : "checkmark")
                                }
                                .tint(.green)
                            }
                    }
                } header: {
                    HStack(spacing: 4) {
                        Text(state.summary)
                        Text("·")
                        Text(state.syncLabel).foregroundStyle(state.syncFailed ? Color.red : Color.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            // Awaits the shared suspend function, so the spinner stays until sync finishes.
            .refreshable { try? await vm.refresh() }
            .animation(.default, value: state.items)
            .navigationTitle("My To-Do")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Clear done") { vm.clearCompleted() }
                        .disabled(!state.canClearCompleted)
                }
            }
            .searchable(text: $query, prompt: "Search")
            .onChange(of: query) { _, newValue in vm.onQueryChange(query: newValue) }
            .onChange(of: draft) { _, newValue in vm.onDraftTitleChange(title: newValue) }
            .sheet(item: $editing) { todo in
                EditTodoView(todo: todo, priorities: state.priorities) { title, notes, priority in
                    vm.updateTodo(id: todo.id, title: title, notes: notes, priority: priority)
                }
            }
            .overlay(alignment: .bottom) { undoBanner }
        }
    }

    private var addRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("What needs to be done?", text: $draft)
                    .focused($draftFocused)
                    .submitLabel(.done)
                    .onSubmit(addDraft)
                Button(action: addDraft) {
                    Image(systemName: "plus.circle.fill").font(.title2)
                }
                .buttonStyle(.borderless)
                .disabled(TodoRules.shared.validateTitle(raw: draft) != nil)
            }
            if let error = state.draftError {
                Text(error).font(.caption).foregroundStyle(.red)
            }
            Picker("Priority", selection: Binding(get: { state.draftPriority }, set: { vm.onDraftPriorityChange(priority: $0) })) {
                ForEach(state.priorities, id: \.self) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
        }
    }

    /// The text field owns its text; clear it only when the shared view model accepts the draft.
    private func addDraft() {
        if vm.addTodo() { draft = "" }
    }

    @ViewBuilder
    private var undoBanner: some View {
        if let deleted = state.recentlyDeleted {
            HStack {
                Text("Deleted \"\(deleted.title)\"").lineLimit(1)
                Spacer()
                Button("Undo") { vm.undoDelete() }.bold()
            }
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            .padding()
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: deleted.id) {
                try? await Task.sleep(for: .seconds(4))
                if !Task.isCancelled { vm.dismissUndo() }
            }
        }
    }
}

private struct TodoRow: View {
    let todo: Todo
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onToggle) {
                Image(systemName: todo.isDone ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(todo.isDone ? Color.green : Color.secondary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(todo.isDone ? "Mark as not done" : "Mark as done")

            VStack(alignment: .leading, spacing: 2) {
                Text(todo.title)
                    .strikethrough(todo.isDone)
                    .foregroundStyle(todo.isDone ? .secondary : .primary)
                    .lineLimit(2)
                if !todo.notes.isEmpty {
                    Text(todo.notes).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer()
            if todo.isPendingSync {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .accessibilityLabel("Waiting to sync")
            }
            if !todo.isDone && todo.priority != .normal {
                Circle()
                    .fill(todo.priority == .high ? Color.red : Color.gray)
                    .frame(width: 10, height: 10)
                    .accessibilityLabel("\(todo.priority.label) priority")
            }
        }
    }
}

extension Todo: @retroactive Identifiable {}
