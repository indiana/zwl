import SwiftUI

/// In-map progress card shown while downloading an offline area (Android
/// `MapViewContainer` parity). Displays status text and a linear progress bar.
struct MapDownloadCard: View {
    let text: String
    let progress: Float
    let isDownloading: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(isDownloading ? "Pobieranie mapy offline..." : "Pobieranie zakończone",
                  systemImage: isDownloading ? "arrow.down.circle" : "checkmark.circle.fill")
                .font(.footnote.weight(.semibold))
                .foregroundColor(.primary)

            if isDownloading {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(ZWL.forestGreenAccent)
            }

            Text(text)
                .font(.caption2)
                .foregroundColor(.secondary)
                .lineLimit(2)
        }
        .padding(12)
        .frame(width: 220)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}