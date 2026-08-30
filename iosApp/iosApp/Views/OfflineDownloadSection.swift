import SwiftUI

/// In-map progress card shown while downloading an offline area (Android
/// `MapViewContainer` parity). Displays status text and a linear progress bar.
struct MapDownloadCard: View {
    let text: String
    let progress: Float
    let isDownloading: Bool
    let errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(headerText, systemImage: headerIcon)
                .font(.footnote.weight(.semibold))
                .foregroundColor(headerColor)

            if isDownloading {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(ZWL.forestGreenAccent)
            }

            ScrollView {
                Text(text)
                    .font(.caption2)
                    .fontWeight(errorMessage == nil ? .regular : .semibold)
                    .foregroundColor(errorMessage == nil ? .secondary : .red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxHeight: 76)
        }
        .padding(10)
        .frame(width: 200)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private var headerText: String {
        if isDownloading { return "Pobieranie mapy offline..." }
        if errorMessage != nil { return "Pobieranie nieudane" }
        return "Pobieranie zakończone"
    }

    private var headerIcon: String {
        if isDownloading { return "arrow.down.circle" }
        if errorMessage != nil { return "xmark.circle.fill" }
        return "checkmark.circle.fill"
    }

    private var headerColor: Color {
        if isDownloading { return .primary }
        if errorMessage != nil { return Color.red }
        return .primary
    }
}