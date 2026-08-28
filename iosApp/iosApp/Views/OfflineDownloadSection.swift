import SwiftUI

/// Compact offline-area download section (iOS feature) styled for the dark status screens.
struct OfflineDownloadSection: View {
    let isDownloading: Bool
    let downloadProgress: Float
    let downloadText: String
    let downloadFinished: Bool
    let onDownload: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            if isDownloading {
                ProgressView(value: downloadProgress)
                    .progressViewStyle(.linear)
                    .accentColor(ZWL.forestGreenAccent)
                Text(downloadText)
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else if !downloadText.isEmpty {
                Text(downloadText)
                    .font(.caption)
                    .foregroundColor(downloadFinished ? ZWL.forestGreenAccent : .secondary)
            }
        }

        Button(action: onDownload) {
            Label(isDownloading ? "Pobieranie..." : "Pobierz obszar do trybu offline",
                  systemImage: "arrow.down.circle")
                .font(.subheadline.weight(.medium))
                .padding(.vertical, 6)
                .padding(.horizontal, 14)
                .background(ZWL.greenPrimary.opacity(0.35))
                .clipShape(Capsule())
                .foregroundColor(.white)
        }
        .buttonStyle(.plain)
        .disabled(isDownloading)
        .opacity(isDownloading ? 0.6 : 1)
    }
}