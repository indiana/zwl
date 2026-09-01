import SwiftUI
import shared

@main
struct ZWLApp: App {
    @StateObject private var viewModel: MainViewModel

    init() {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].path
        let app = IosAppBootstrap.shared.setup(cacheDirectory: cacheDir)
        _viewModel = StateObject(wrappedValue: MainViewModel(app: app))
    }

    var body: some Scene {
        WindowGroup {
            MainView(viewModel: viewModel)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    viewModel.openPointFromLink(url)
                }
        }
    }
}