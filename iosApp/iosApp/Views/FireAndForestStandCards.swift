import SwiftUI
import shared

/// Zone-detail data cards shared by `ZoneDetailView` and
/// `SavedPointPropertiesView` (fire risk + stove rules + BDL forest stand).
struct DetailCard<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
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
}

/// "ZAGROŻENIE POŻAROWE" — risk level only, no stove rules. Used for saved
/// points: stove usage is a ZWL-zone concept (outside zones the rules forbid
/// stoves regardless of fire risk).
struct FireRiskCard: View {
    let level: Int?
    let isLoading: Bool

    var body: some View {
        DetailCard(title: "ZAGROŻENIE POŻAROWE") {
            FireRiskBadge(level: level, isLoading: isLoading)
        }
    }
}

private struct FireRiskBadge: View {
    let level: Int?
    let isLoading: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Stopień zagrożenia pożarowego:")
                .font(.system(size: 14))
            if isLoading {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Pobieranie aktualnych danych...")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
            } else if let level = level {
                riskBadge(text: Formatters.fireRiskStatusText(level),
                          color: Formatters.fireRiskColor(level))
            } else {
                riskBadge(text: Formatters.fireRiskStatusText(-2),
                          color: Formatters.fireRiskColor(-2))
            }
        }
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
}

/// "ZAGROŻENIE POŻAROWE I ZASADY" — risk badge + gas-stove rules.
struct FireAndStoveCard: View {
    let level: Int?
    let isLoading: Bool

    var body: some View {
        DetailCard(title: "ZAGROŻENIE POŻAROWE I ZASADY") {
            VStack(alignment: .leading, spacing: 12) {
                FireRiskBadge(level: level, isLoading: isLoading)

                Divider()

                VStack(alignment: .leading, spacing: 6) {
                    Text("Używanie kuchenek gazowych:")
                        .font(.system(size: 14))
                    if isLoading {
                        Text("Pobieranie aktualnych zasad...")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    } else {
                        stoveBadge(for: level)
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
}

/// "STRUKTURA I CHARAKTERYSTYKA DRZEWOSTANU" — BDL summary with species
/// breakdown and the habitat/management metadata (tooltips included).
struct ForestStandCard: View {
    let app: ForestApp
    let summary: ForestStandSummary?
    let isLoading: Bool

    var body: some View {
        DetailCard(title: "STRUKTURA I CHARAKTERYSTYKA DRZEWOSTANU") {
            if isLoading && summary == nil {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                        .tint(ZWL.forestGreenAccent)
                    Text("Pobieranie szczegółowych danych z Banku Danych o Lasach...")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 4)
            } else if let summary = summary, !Self.isEmpty(summary) {
                forestStandContent(summary)
            } else {
                Text("Brak szczegółowych danych o drzewostanie dla wybranego obszaru.")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                    .padding(.vertical, 4)
            }
        }
    }

    private static func isEmpty(_ summary: ForestStandSummary) -> Bool {
        summary.totalAreaHa <= 0 &&
            summary.speciesBreakdown.isEmpty &&
            summary.forestFunction == nil &&
            summary.standStructure == nil &&
            summary.siteType == nil &&
            summary.protectionCategory == nil &&
            summary.rotationAge == nil
    }

    @ViewBuilder
    private func forestStandContent(_ summary: ForestStandSummary) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if summary.totalAreaHa > 0 {
                Text("Powierzchnia drzewostanu: \(oneDecimal(summary.totalAreaHa)) ha")
                    .font(.system(size: 14, weight: .semibold))
            }

            if summary.speciesBreakdown.count > 0 {
                Text("Podział gatunkowy:")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.secondary)
                    .padding(.top, 2)

                ForEach(Array(summary.speciesBreakdown.enumerated()), id: \.offset) { _, entry in
                    speciesRow(entry)
                }
            }

            metadataSection(summary)
        }
    }

    private func speciesRow(_ entry: SpeciesEntry) -> some View {
        let displayName = entry.ageLabel.map { "\(entry.speciesName) (\($0))" } ?? entry.speciesName
        let wikipediaTitle = app.speciesWikipediaTitle(code: entry.speciesCode)
        let hasLink = wikipediaTitle != nil
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(displayName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(hasLink ? ZWL.forestGreenAccent : Color(.label))
                    .underline(hasLink)
                    .onTapGesture {
                        if let title = wikipediaTitle {
                            openWikipedia(title)
                        }
                    }
                Spacer()
                Text("\(oneDecimal(entry.percentage))%")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(ZWL.forestGreenAccent)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color(.systemFill))
                    Capsule()
                        .fill(ZWL.forestGreenAccent)
                        .frame(width: max(geo.size.width * CGFloat(min(entry.percentage / 100.0, 1.0)), 0))
                }
            }
            .frame(height: 6)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func metadataSection(_ summary: ForestStandSummary) -> some View {
        let items = metadataItems(summary)
        if !items.isEmpty {
            Divider()
                .padding(.vertical, 4)
            Text("Parametry siedliska i gospodarki:")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.secondary)
            ForEach(items.indices, id: \.self) { index in
                HStack(alignment: .top) {
                    Text(items[index].label)
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                    Spacer()
                    metadataValue(items[index])
                }
                .padding(.vertical, 2)
            }
        }
    }

    private func metadataItems(_ summary: ForestStandSummary) -> [(label: String, value: String, tooltip: String?)] {
        var items: [(label: String, value: String, tooltip: String?)] = []
        if let item = summary.forestFunction {
            items.append(("Funkcja lasu", item.name,
                          app.forestFunTooltip(code: item.code)))
        }
        if let item = summary.standStructure {
            items.append(("Struktura drzewostanu", item.name,
                          app.standStruTooltip(code: item.code)))
        }
        if let item = summary.siteType {
            items.append(("Typ siedliskowy lasu", item.name,
                          app.siteTypeTooltip(code: item.code)))
        }
        if let item = summary.protectionCategory {
            items.append(("Kategoria ochrony", item.name,
                          app.protCategTooltip(code: item.code)))
        }
        if let rotationAge = app.forestStandRotationAgeText(summary: summary) {
            items.append(("Wiek rębności", rotationAge, app.rotationAgeTooltip()))
        }
        return items
    }

    @ViewBuilder
    private func metadataValue(_ item: (label: String, value: String, tooltip: String?)) -> some View {
        if let tooltip = item.tooltip {
            MetadataValueWithTooltip(value: item.value, tooltip: tooltip)
        } else {
            Text(item.value)
                .font(.system(size: 13, weight: .medium))
        }
    }

    private func oneDecimal(_ value: Double) -> String {
        // 'en_US_POSIX' mirrors Android's Locale.US formatting (always '.', never ',').
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private func openWikipedia(_ articleTitle: String) {
        guard let url = URL(string: app.wikipediaPageUrl(title: articleTitle)) else { return }
        UIApplication.shared.open(url)
    }
}

/// Metadata value with an info icon; tapping it toggles a small persistent
/// tooltip (Android `MetadataValueWithTooltip` parity — 'info' + tap to show
/// the explanation).
struct MetadataValueWithTooltip: View {
    let value: String
    let tooltip: String
    @State private var isShowing = false

    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            HStack(spacing: 4) {
                Text(value)
                    .font(.system(size: 13, weight: .medium))
                Image(systemName: "info.circle")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                isShowing.toggle()
            }
            if isShowing {
                Text(tooltip)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .padding(8)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .transition(.opacity)
            }
        }
    }
}
