import SwiftUI
import shared

struct PoiDetailView: View {
    let poi: Poi

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Image(systemName: poiIcon)
                        .foregroundColor(poiColor)
                        .font(.title)
                    VStack(alignment: .leading) {
                        Text(poi.name.isEmpty ? "Punkt informacyjny" : poi.name)
                            .font(.headline)
                        Text(poi.classify().displayName())
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }

                if !poi.description.isEmpty {
                    Text(poi.description)
                        .font(.body)
                }

                if !poi.code.isEmpty {
                    Text("Kod: \(poi.code)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Punkt")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        dismiss()
                    }
                }
            }
        }
    }

    private var poiCategory: PoiCategory { poi.classify() }

    private var poiColor: Color {
        switch poiCategory {
        case .shelter: return .green
        case .fireplace: return .orange
        default: return .blue
        }
    }

    private var poiIcon: String {
        switch poiCategory {
        case .shelter: return "tent.fill"
        case .fireplace: return "flame.fill"
        default: return "mappin.circle.fill"
        }
    }

    @Environment(\.dismiss) private var dismiss
}