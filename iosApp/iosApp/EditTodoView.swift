import SwiftUI
import Shared

struct EditTodoView: View {
    let todo: Todo
    let priorities: [Priority]
    /// Returns a validation error from the shared rules, or nil when saved.
    let onSave: (String, String, Priority) -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var title: String
    @State private var notes: String
    @State private var priority: Priority
    @State private var error: String?

    init(todo: Todo, priorities: [Priority], onSave: @escaping (String, String, Priority) -> String?) {
        self.todo = todo
        self.priorities = priorities
        self.onSave = onSave
        _title = State(initialValue: todo.title)
        _notes = State(initialValue: todo.notes)
        _priority = State(initialValue: todo.priority)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $title)
                        .onChange(of: title) { error = nil }
                    if let error {
                        Text(error).font(.caption).foregroundStyle(.red)
                    }
                }
                Section("Notes") {
                    TextField("Notes", text: $notes, axis: .vertical).lineLimit(3...8)
                }
                Section("Priority") {
                    Picker("Priority", selection: $priority) {
                        ForEach(priorities, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("Edit To-Do")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        error = onSave(title, notes, priority)
                        if error == nil { dismiss() }
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
