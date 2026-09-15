import Foundation
import SwiftUI
import shared

@MainActor
final class AppUpdateService: ObservableObject {

    struct UpdatePrompt: Identifiable {
        let id = "app-update"
        let storeVersion: String
        let storeUrl: URL
    }

    @Published var prompt: UpdatePrompt?

    private static let lastCheckKey = "lastAppUpdateCheckAt"
    private static let checkInterval: TimeInterval = 6 * 3600

    func checkIfNeeded() async {
        let defaults = UserDefaults.standard
        if let last = defaults.object(forKey: Self.lastCheckKey) as? Date,
           Date().timeIntervalSince(last) < Self.checkInterval {
            return
        }
        guard let bundleId = Bundle.main.bundleIdentifier,
              let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        else { return }

        var components = URLComponents(string: "https://itunes.apple.com/lookup")
        components?.queryItems = [
            URLQueryItem(name: "bundleId", value: bundleId),
            URLQueryItem(name: "country", value: "PL")
        ]
        guard let url = components?.url else { return }
        let session = URLSession(configuration: .ephemeral)
        do {
            let (data, response) = try await session.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
            let decoded = try JSONDecoder().decode(LookupResponse.self, from: data)
            guard let latest = decoded.results.first,
                  let trackUrl = latest.trackViewUrl,
                  let storeUrl = URL(string: trackUrl)
            else { return }
            defaults.set(Date(), forKey: Self.lastCheckKey)
            guard UpdateTools.shared.isUpdateAvailable(installed: current, store: latest.version) else { return }
            prompt = UpdatePrompt(storeVersion: latest.version, storeUrl: storeUrl)
        } catch {
        }
    }
}

private struct LookupResponse: Decodable {
    let results: [LookupResult]
}

private struct LookupResult: Decodable {
    let version: String
    let trackViewUrl: String?
}
