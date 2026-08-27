import SwiftUI

struct OutsideZoneView: View {
    let nearestDistrict: String
    let distanceMeters: Double
    let bearingDegrees: Float
    let isDownloading: Bool
    let downloadProgress: Float
    let downloadText: String
    let downloadFinished: Bool
    let onDownload: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "figure.hiking")
                    .foregroundColor(.orange)
                Text("Jesteś poza strefą")
                    .font(.headline)
                Spacer()
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Najbliższa strefa:")
                    .font(.caption)
                    .foregroundColor(.secondary)
                HStack(spacing: 12) {
                    Text(nearestDistrict)
                        .font(.headline)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(Formatters.distanceText(distanceMeters)) \(Formatters.bearingText(bearingDegrees))")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            if isDownloading {
                ProgressView(value: downloadProgress)
                Text(downloadText)
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else if !downloadText.isEmpty {
                Text(downloadText)
                    .font(.caption)
                    .foregroundColor(downloadFinished ? .green : .secondary)
            }

            Button(action: onDownload) {
                Label(isDownloading ? "Pobieranie..." : "Pobierz obszar do trybu offline",
                      systemImage: "arrow.down.circle")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .disabled(isDownloading)
        }
        .padding()
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding([.horizontal, .bottom])
    }
}