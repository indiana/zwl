import SwiftUI
import shared

/// POI sheet, Android `PoiDetailsCard` parity: title + category header with a
/// close button, distance-from-user subcard and the BDL category description.
struct PoiDetailView: View {
    let poi: Poi
    let distanceMeters: Double?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerRow

                distanceSubcard

                if showDescription {
                    descriptionSubcard
                }

                Spacer(minLength: 8)
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
    }

    private var headerRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(poi.name.isEmpty ? "Obiekt rekreacyjny" : poi.name)
                    .font(.system(size: 18, weight: .bold))
                Text(poi.classify().displayName())
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.secondary)
                    .frame(width: 28, height: 28)
                    .background(Color(.secondarySystemBackground), in: Circle())
            }
        }
    }

    private var distanceSubcard: some View {
        subcard {
            Text("ODLEGŁOŚĆ OD TWOJEJ POZYCJI")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(distanceText)
                .font(.system(size: 16, weight: .bold))
                .padding(.top, 4)
        }
    }

    private var showDescription: Bool {
        !poi.description.isEmpty && poi.description != poi.name
    }

    private var descriptionSubcard: some View {
        subcard {
            Text("OPIS KATEGORII BDL")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(poi.description)
                .font(.system(size: 13))
                .padding(.top, 4)
        }
    }

    private func subcard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var distanceText: String {
        guard let meters = distanceMeters else { return "Obliczanie..." }
        return Formatters.distanceText(meters)
    }

    @Environment(\.dismiss) private var dismiss
}