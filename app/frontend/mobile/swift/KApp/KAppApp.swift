import SwiftUI

@main
struct KAppApp: App {
    @State private var app = AppModel.live()

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
                .tint(KColor.action)
        }
    }
}
