import SwiftUI
import shared

/// "Pobliskie punkty" section for the point detail sheets (Android
/// `NearbyPointsSection` parity): nearby saved points, POIs and water sources
/// sorted by distance, capped by the shared use case. The empty state is a
/// single line — no header, no container — so it stays unobtrusive when
/// nothing is nearby. Radius 0 turns the feature off and the section is not
/// rendered at all. Tapping a row opens that point's detail.
struct NearbyPointsSection: View {
    let points: [NearbyPoint]
    let totalCount: Int
    let radiusKm: Float
    let onSelect: (NearbyPoint) -> Void

    var body: some View {
        // Radius 0 = feature off: no section anywhere.
        if radiusKm > 0 {
            content
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 6) {
            if points.isEmpty {
                Text("Brak innych punktów w promieniu \(Self.radiusText(radiusKm))")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            } else {
                Text("POBLISKIE PUNKTY (\(totalCount))")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.secondary)

                ForEach(points.indices, id: \.self) { index in
                    row(points[index])
                }

                let hidden = totalCount - points.count
                if hidden > 0 {
                    Text("…i \(hidden) więcej w promieniu")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .padding(.top, 2)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func row(_ point: NearbyPoint) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(dotColor(for: point))
                .frame(width: 10, height: 10)

            VStack(alignment: .leading, spacing: 1) {
                Text(point.name)
                    .font(.system(size: 14))
                    .foregroundStyle(.primary)
                Text(point.subtitle.isEmpty ? "Mój punkt" : point.subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            Text(Formatters.distanceText(point.distanceMeters))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.primary)
        }
        .contentShape(Rectangle())
        .onTapGesture { onSelect(point) }
    }

    private func dotColor(for point: NearbyPoint) -> Color {
        switch point.type {
        case .ownPoint: return Self.ownPointColor
        case .poi: return Self.poiColor
        case .waterSource: return Self.waterColor
        @unknown default: return Self.poiColor
        }
    }

    /// Same format the detail cards use (whole meters below 100 m, one-decimal
    /// km above); radius text keeps the app-wide "5.0 km" convention.
    private static func radiusText(_ radiusKm: Float) -> String {
        String(format: "%.1f km", radiusKm)
    }

    // Map layer colors (Android parity): own points magenta, POIs/inne blue,
    // water blue-dark.
    private static let ownPointColor = Color(red: 233.0 / 255.0, green: 30.0 / 255.0, blue: 99.0 / 255.0)
    private static let poiColor = Color(red: 25.0 / 255.0, green: 118.0 / 255.0, blue: 210.0 / 255.0)
    private static let waterColor = Color(red: 21.0 / 255.0, green: 101.0 / 255.0, blue: 192.0 / 255.0)
}
