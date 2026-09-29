import SwiftUI
import shared

/// Water-source sheet, Android `WaterSourceDetailsCard` parity: title + type
/// header with a close button, water status, optional well depth,
/// distance-from-user and the OSM ODbL attribution + disclaimer.
struct WaterSourceDetailView: View {
    let waterSource: WaterSource
    let distanceMeters: Double?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerRow

                infoSubcard(title: "STATUS WODY", value: drinkingWaterLabel)

                if let depth = waterSource.depthMeters?.doubleValue {
                    infoSubcard(title: "GŁĘBOKOŚĆ", value: "\(Int(depth)) m")
                }

                if let pump = waterSource.pump {
                    infoSubcard(title: "POMPA", value: Self.pumpLabel(pump))
                }

                if let fountain = waterSource.fountain {
                    infoSubcard(title: "RODZAJ PUNKTU", value: Self.fountainLabel(fountain))
                }

                if let bottle = waterSource.bottle, let label = Self.bottleLabel(bottle) {
                    infoSubcard(title: "NAPEŁNIANIE BUTELKI", value: label)
                }

                if let raw = waterSource.drinkingWaterRaw, let label = Self.drinkingWaterRawLabel(raw) {
                    infoSubcard(title: "SZCZEGÓŁY WODY", value: label)
                }

                if let seasonal = waterSource.seasonal {
                    infoSubcard(title: "SEZONOWOŚĆ", value: Self.seasonalLabel(seasonal))
                }

                if let intermittent = waterSource.intermittent {
                    infoSubcard(title: "DOSTĘPNOŚĆ", value: Self.intermittentLabel(intermittent))
                }

                if let fee = waterSource.fee {
                    infoSubcard(title: "OPŁATA", value: Self.feeLabel(fee))
                }

                if let openingHours = waterSource.openingHours {
                    infoSubcard(title: "GODZINY OTWARCIA", value: openingHours)
                }

                if let operatorName = waterSource.operator_ {
                    infoSubcard(title: "OPERATOR", value: operatorName)
                }

                if let description = waterSource.description_ {
                    infoSubcard(title: "OPIS", value: description)
                }

                infoSubcard(title: "ODLEGŁOŚĆ OD TWOJEJ POZYCJI", value: distanceText)

                attributionSubcard

                Spacer(minLength: 8)
            }
            .padding(16)
        }
        .background(Color(.systemBackground))
    }

    private var headerRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(waterSource.name.isEmpty ? "Źródło wody" : waterSource.name)
                    .font(.system(size: 18, weight: .bold))
                Text(typeLabel)
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

    private var attributionSubcard: some View {
        subcard {
            Text("ŹRÓDŁO DANYCH")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(WaterAttribution.shared.OSM)
                .font(.system(size: 13))
                .padding(.top, 4)
            Text(WaterAttribution.shared.WATER_OSM_DISCLAIMER)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
                .padding(.top, 6)
            if waterSource.drinkingWater.name != "YES" {
                Text(WaterAttribution.shared.WATER_POTENTIAL_DISCLAIMER)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(ZWL.errorRedAccent)
                    .padding(.top, 6)
            }
        }
    }

    private func infoSubcard(title: String, value: String) -> some View {
        subcard {
            Text(title)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(size: 16, weight: .bold))
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

    private var typeLabel: String {
        switch waterSource.type.name {
        case "DRINKING_WATER": return "Woda pitna"
        case "WATER_TAP": return "Kran z wodą"
        case "WATER_POINT": return "Punkt poboru wody"
        case "SPRING": return "Źródło"
        case "WELL": return "Studnia"
        case "FOUNTAIN": return "Fontanna"
        case "WATER_ON_SITE": return "Woda na miejscu (biwak/wiata)"
        case "REFILL": return "Punkt napełniania butelek"
        default: return "Źródło wody"
        }
    }

    private static func pumpLabel(_ raw: String) -> String {
        switch firstToken(raw) {
        case "manual", "hand_pump": return "Pompa ręczna"
        case "no": return "Otwarty szyb (własna lina)"
        case "powered": return "Pompa mechaniczna"
        case "automatic": return "Pompa automatyczna"
        case "yes": return "Pompa (rodzaj nieznany)"
        default: return raw
        }
    }

    private static func drinkingWaterRawLabel(_ raw: String) -> String? {
        switch raw.lowercased() {
        case "boil": return "Woda pitna po przegotowaniu"
        case "treated": return "Woda uzdatniona"
        case "untreated": return "Woda nieuzdatniona"
        case "mineral": return "Woda mineralna"
        case "seasonal": return "Dostępna sezonowo"
        case "conditional": return "Dostępna warunkowo"
        case "manantial": return "Źródło"
        case "bubbler": return "Poidełko"
        case "fountain": return "Fontanna"
        case "yes", "true", "1", "no", "false", "0", "unknown", "fixme": return nil
        default: return raw
        }
    }

    private static func seasonalLabel(_ raw: String) -> String {
        translateTokens(raw) { token in
            switch token {
            case "yes", "true", "1": return "sezonowo"
            case "no", "false", "0": return "całorocznie"
            case "spring": return "wiosna"
            case "summer": return "lato"
            case "autumn": return "jesień"
            case "winter": return "zima"
            case "wet_season": return "pora deszczowa"
            case "dry_season": return "pora sucha"
            default: return token
            }
        }
    }

    private static func fountainLabel(_ raw: String) -> String {
        translateTokens(raw) { token in
            switch token {
            case "bubbler": return "poidełko"
            case "drinking": return "fontanna pitna"
            case "bottle_refill": return "napełnianie butelek"
            case "water_tap", "tap": return "kran"
            case "nozzle": return "dysza"
            case "stone_block": return "blok kamienny"
            case "water_dispenser": return "dozownik wody"
            case "decorative": return "dekoracyjna"
            case "yes": return "fontanna"
            default: return token
            }
        }
    }

    private static func bottleLabel(_ raw: String) -> String? {
        switch raw.lowercased() {
        case "yes": return "Można napełnić butelkę"
        case "designated": return "Wyznaczone do napełniania"
        case "limited": return "Ograniczona możliwość"
        case "no", "false", "0": return nil
        default: return raw
        }
    }

    private static func firstToken(_ raw: String) -> String {
        let token = raw.lowercased()
            .components(separatedBy: CharacterSet(charactersIn: ";, "))
            .first { !$0.isEmpty }
        return token?.trimmingCharacters(in: .whitespaces) ?? ""
    }

    private static func translateTokens(_ raw: String, _ translate: (String) -> String) -> String {
        let parts = raw.lowercased()
            .components(separatedBy: CharacterSet(charactersIn: ";,"))
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        if parts.isEmpty { return raw }
        let joined = parts.map(translate).joined(separator: ", ")
        return joined.prefix(1).uppercased() + String(joined.dropFirst())
    }

    private static func intermittentLabel(_ raw: String) -> String {
        switch raw.lowercased() {
        case "yes", "true", "1": return "Okresowo (może nie działać)"
        case "no", "false", "0": return "Stale"
        default: return raw
        }
    }

    private static func feeLabel(_ raw: String) -> String {
        switch raw.lowercased() {
        case "yes", "true", "1": return "Płatne"
        case "no", "false", "0": return "Bezpłatne"
        default: return raw
        }
    }

    private var drinkingWaterLabel: String {
        switch waterSource.drinkingWater.name {
        case "YES": return "Pitna (potwierdzona)"
        case "NO": return "Niepitna"
        default: return "Nieznany"
        }
    }

    private var distanceText: String {
        guard let meters = distanceMeters else { return "Obliczanie..." }
        return Formatters.distanceText(meters)
    }

    @Environment(\.dismiss) private var dismiss
}
