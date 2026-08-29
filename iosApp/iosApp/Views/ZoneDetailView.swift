import SwiftUI
import shared

/// Zone property sheet, Android `ZoneDetailsScreen` parity (cycle 1: no BDL
/// forest-stand card — that lands in cycle 2).
struct ZoneDetailView: View {
    let zone: Zone
    let distanceMeters: Double?
    let fireRiskLevel: Int?
    let isLoadingFireRisk: Bool

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let urlString = zone.websiteUrl {
                        detailCard(title: "STRONA NADLEŚNICTWA") {
                            Link(destination: URL(string: urlString) ??
                                 URL(string: "https://pl.wikipedia.org")!) {
                                Label(urlString.replacingOccurrences(of: "https://", with: ""),
                                      systemImage: "globe")
                                    .font(.subheadline)
                                    .foregroundColor(ZWL.forestGreenAccent)
                            }
                        }
                    }

                    detailCard(title: "ODLEGŁOŚĆ OD LOKALIZACJI") {
                        Text(distanceText)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(ZWL.forestGreenAccent)
                    }

                    fireAndStoveCard

                    Spacer(minLength: 8)
                }
                .padding(16)
            }
            .navigationTitle("Strefa")
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

    private var distanceText: String {
        guard let meters = distanceMeters else { return "Obliczanie odległości..." }
        if meters == 0.0 { return "Jesteś na terenie tej strefy" }
        return Formatters.distanceText(meters)
    }

    private var fireAndStoveCard: some View {
        detailCard(title: "ZAGROŻENIE POŻAROWE I ZASADY") {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Stopień zagrożenia pożarowego:")
                        .font(.system(size: 14))
                    if isLoadingFireRisk {
                        HStack(spacing: 8) {
                            ProgressView()
                                .controlSize(.small)
                            Text("Pobieranie aktualnych danych...")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                    } else if let level = fireRiskLevel {
                        riskBadge(text: Formatters.fireRiskStatusText(level),
                                  color: Formatters.fireRiskColor(level))
                    } else {
                        riskBadge(text: Formatters.fireRiskStatusText(-2),
                                  color: Formatters.fireRiskColor(-2))
                    }
                }

                Divider()

                VStack(alignment: .leading, spacing: 6) {
                    Text("Używanie kuchenek gazowych:")
                        .font(.system(size: 14))
                    if isLoadingFireRisk {
                        Text("Pobieranie aktualnych zasad...")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    } else {
                        stoveBadge(for: fireRiskLevel)
                    }
                    Text(Formatters.gasStoveDisclaimer)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .lineSpacing(2)
                }
            }
        }
    }

    @ViewBuilder
    private func stoveBadge(for level: Int?) -> some View {
        switch Formatters.stoveRule(for: level ?? -2) {
        case .allowed(let text):
            stoveBadgeText(text, color: ZWL.greenPrimary, textColor: ZWL.forestGreenAccent)
        case .ban(let text):
            stoveBadgeText(text, color: ZWL.errorRedButton, textColor: ZWL.errorRedAccent, pulsing: true)
        case .noData:
            stoveBadgeText("WARUNKOWO DOZWOLONE (brak danych pożarowych)",
                           color: ZWL.yellowPrimary, textColor: ZWL.riskLow, fontSize: 13)
        }
    }

    private func stoveBadgeText(_ text: String, color: Color, textColor: Color,
                                pulsing: Bool = false, fontSize: CGFloat = 15) -> some View {
        Text(text)
            .font(.system(size: fontSize, weight: .bold))
            .foregroundColor(textColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(color.opacity(pulsing ? 0.2 : 0.15))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color, lineWidth: pulsing ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func riskBadge(text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 15, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(color.opacity(0.15))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(color, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func detailCard<Content: View>(title: String,
                                           @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @Environment(\.dismiss) private var dismiss
}