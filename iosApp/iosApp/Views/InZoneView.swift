import SwiftUI

struct InZoneView: View {
    let district: String
    let fireRisk: Int
    let isDownloading: Bool
    let downloadProgress: Float
    let downloadText: String
    let downloadFinished: Bool
    let onDownload: () -> Void

    private var fireRiskColor: Color {
        switch fireRisk {
        case 1: return .green
        case 2: return .orange
        case 3: return .red
        default: return .secondary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundColor(.green)
                Text("Jesteś w strefie Zanocuj w Lesie")
                    .font(.headline)
                Spacer()
            }

            Text(district)
                .font(.subheadline)
                .foregroundColor(.secondary)

            HStack {
                Text("Zagrożenie pożarowe:")
                    .font(.subheadline)
                if let risk = Formatters.fireRiskText(fireRisk) {
                    Text(risk)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(fireRiskColor)
                } else {
                    Text("-")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
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