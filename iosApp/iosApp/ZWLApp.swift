import SwiftUI
import UIKit
import shared

@main
struct ZWLApp: App {
    @StateObject private var viewModel: MainViewModel
    @StateObject private var updateService = AppUpdateService()

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
                .task {
                    await updateService.checkIfNeeded()
                }
                .alert(
                    "Dostępna aktualizacja",
                    isPresented: Binding(
                        get: { updateService.prompt != nil },
                        set: { if !$0 { updateService.prompt = nil } }
                    ),
                    actions: {
                        Button("Aktualizuj") {
                            if let url = updateService.prompt?.storeUrl {
                                UIApplication.shared.open(url)
                            }
                            updateService.prompt = nil
                        }
                        Button("Później", role: .cancel) {}
                    },
                    message: {
                        Text("Nowa wersja \(updateService.prompt?.storeVersion ?? "") jest dostępna w App Store.")
                    }
                )
        }
    }
}