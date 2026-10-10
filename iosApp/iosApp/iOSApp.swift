import SwiftUI

@main
struct iOSApp: App {
    @State private var model = TodoListModel()

    var body: some Scene {
        WindowGroup {
            TodoListView(model: model)
        }
    }
}
