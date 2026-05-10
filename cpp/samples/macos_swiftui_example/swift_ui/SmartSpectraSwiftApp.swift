import SwiftUI

@main
struct SmartSpectraSwiftApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .frame(minWidth: 1040, minHeight: 680)
        }
    }
}
