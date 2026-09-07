import SwiftUI
import shared

/// Zone property sheet, Android `ZoneDetailsScreen` parity (distance + fire
/// risk/stove rule + BDL forest-stand card).
struct ZoneDetailView: View {
    let zone: Zone
    let app: ForestApp
    let distanceMeters: Double?
    let fireRiskLevel: Int?
    let isLoadingFireRisk: Bool
    let forestStand: ForestStandSummary?
    let isLoadingForestStand: Bool

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let urlString = zone.websiteUrl {
                        DetailCard(title: "STRONA NADLEŚNICTWA") {
                            Link(destination: URL(string: urlString) ??
                                 URL(string: "https://pl.wikipedia.org")!) {
                                Label(urlString.replacingOccurrences(of: "https://", with: ""),
                                      systemImage: "globe")
                                    .font(.subheadline)
                                    .foregroundColor(ZWL.forestGreenAccent)
                            }
                        }
                    }

                    DetailCard(title: "ODLEGŁOŚĆ OD LOKALIZACJI") {
                        Text(distanceText)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(ZWL.forestGreenAccent)
                    }

                    FireAndStoveCard(level: fireRiskLevel, isLoading: isLoadingFireRisk)

                    ForestStandCard(app: app, summary: forestStand, isLoading: isLoadingForestStand)

                    Spacer(minLength: 8)
                }
                .padding(16)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text(zone.forestDistrict)
                            .font(.system(size: 17, weight: .bold))
                        Text("Strefa programu \"Zanocuj w Lesie\"")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        dismiss()
                    }
                }
            }
        }
    }

    private var distanceText: String {
        guard let meters = distanceMeters else { return "Obliczanie odległości..." }
        if meters == 0.0 { return "Jesteś na terenie tej strefy" }
        return Formatters.distanceText(meters)
    }

    @Environment(\.dismiss) private var dismiss
}
