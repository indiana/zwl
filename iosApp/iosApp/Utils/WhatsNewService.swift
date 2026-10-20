import Foundation
import SwiftUI
import shared

/// "Co nowego": release notes shown once per marketing version, on both an
/// update and a fresh install. In Debug builds the latest note always shows
/// and the seen marker is never written, so the popup can be exercised on
/// every launch.
@MainActor
final class WhatsNewService: ObservableObject {

    @Published var note: ReleaseNote?

    private static let lastSeenKey = "mapSettings.lastSeenVersion"

    private static var isDebugBuild: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    func check() {
        guard let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        else { return }
        let lastSeen = UserDefaults.standard.string(forKey: Self.lastSeenKey)
        guard let note = WhatsNew.shared.noteToShow(
            currentVersion: current,
            lastSeenVersion: lastSeen,
            debugBuild: Self.isDebugBuild
        ) else { return }
        self.note = note
    }

    func dismiss() {
        if let note, !Self.isDebugBuild {
            UserDefaults.standard.set(note.version, forKey: Self.lastSeenKey)
        }
        self.note = nil
    }
}
