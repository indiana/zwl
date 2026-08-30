import SwiftUI
import shared

/// Forest-ban sheet, Android `ForestBanDetailsScreen` parity: alert banner,
/// validity period, nadleśnictwo website and forest-localisation cards.
struct ForestBanDetailView: View {
    let ban: ForestBan
    let app: ForestApp

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    alertBannerCard
                    periodCard
                    if let url = nadlesnictwoUrl {
                        websiteCard(url)
                    }
                    localisationCard
                }
                .padding(16)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text("Zakaz wstępu do lasu")
                            .font(.system(size: 17, weight: .bold))
                        Text(ban.forestDistrictName)
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

    // MARK: - Card 1: Alert banner

    private var alertBannerCard: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle()
                    .fill(ZWL.errorRedButton.opacity(0.25))
                    .frame(width: 56, height: 56)
                Image(systemName: "nosign")
                    .font(.system(size: 36))
                    .foregroundColor(ZWL.errorRedAccent)
            }

            Text("OBSZAR OBJĘTY ZAKAZEM WSTĘPU")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(ZWL.errorRedAccent)
                .kerning(1)
                .multilineTextAlignment(.center)
                .padding(.top, 12)

            Text(ban.reason.uppercased())
                .font(.system(size: 18, weight: .black))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.top, 6)

            if !ban.description.isEmpty {
                Divider()
                    .overlay(ZWL.errorRedAccent.opacity(0.3))
                    .padding(.vertical, 10)

                Text(ban.description)
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(ZWL.errorDarkBackground)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ZWL.errorRedAccent, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Card 2: Validity period

    private var periodCard: some View {
        card {
            headerRow(icon: "calendar", title: "Okres obowiązywania zakazu")

            if let start = ban.startDate {
                periodRow(label: "Wprowadzono:", value: start, bold: false)
            }
            if let end = ban.endDate {
                periodRow(label: "Obowiązuje do:", value: end, bold: true)
            }
        }
    }

    private func periodRow(label: String, value: String, bold: Bool) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: bold ? .bold : .semibold))
        }
        .padding(.vertical, 2)
    }

    // MARK: - Card 3: Nadleśnictwo website

    private func websiteCard(_ urlString: String) -> some View {
        card {
            headerRow(icon: "globe", title: "Strona nadleśnictwa")

            Link(destination: URL(string: urlString) ?? URL(string: "https://pl.wikipedia.org")!) {
                Label(app.nadlesnictwoWebsiteHost(url: urlString) ?? urlString,
                      systemImage: "globe.americas")
                    .font(.subheadline)
                    .foregroundColor(ZWL.forestGreenAccent)
            }
            .padding(.top, 12)
        }
    }

    // MARK: - Card 4: Forest localisation

    private var localisationCard: some View {
        card {
            headerRow(icon: "mappin.and.ellipse", title: "Lokalizacja leśna")

            localisationRow(label: "RDLP:", value: ban.rdlpName)
            if let code = ban.forestDistrictCode {
                localisationRow(label: "Nadleśnictwo:",
                                value: "\(ban.forestDistrictName) (\(code))")
            } else {
                localisationRow(label: "Nadleśnictwo:",
                                value: ban.forestDistrictName)
            }
            localisationRow(label: "Leśnictwo:", value: ban.forestryName)

            if let area = ban.areaSqMeters {
                localisationRow(label: "Powierzchnia:",
                                value: "\(formatAreaHa(area)) ha (\(formatAreaSqM(area)) m²)")
            }
        }
    }

    private func localisationRow(label: String, value: String?) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            Spacer()
            Text(value ?? "")
                .font(.system(size: 13, weight: .medium))
        }
        .padding(.vertical, 3)
    }

    // MARK: - Shared helpers

    private func card(@ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func headerRow(icon: String, title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(ZWL.forestGreenAccent)
            Text(title)
                .font(.system(size: 15, weight: .bold))
        }
    }

    private var nadlesnictwoUrl: String? {
        app.nadlesnictwoWebsiteUrl(districtName: ban.forestDistrictName,
                                   rdlpName: ban.rdlpName)
    }

    private func formatAreaHa(_ sqMeters: Double) -> String {
        formatArea(sqMeters / 10000.0, decimals: 2)
    }

    private func formatAreaSqM(_ sqMeters: Double) -> String {
        formatArea(sqMeters, decimals: 0)
    }

    /// Mirrors Android `formatAreaHa`/`formatAreaSqM`: space-thousands grouping,
    /// always '.' decimals (Locale.ROOT), HALF_UP rounding.
    private func formatArea(_ value: Double, decimals: Int) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.minimumFractionDigits = decimals
        formatter.maximumFractionDigits = decimals
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.\(decimals)f", value)
    }

    @Environment(\.dismiss) private var dismiss
}