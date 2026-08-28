import Foundation
import SwiftUI

enum Formatters {

    /// Formats `distanceMeters` like the Android app: m for < 1000, km above.
    static func distanceText(_ meters: Double) -> String {
        if meters < 1000 {
            return String(format: "%.0f m", meters)
        }
        return String(format: "%.1f km", meters / 1000.0)
    }

    /// Nearest-zone card distance, exactly like Android `OutsideZoneContent.formatDistance`.
    static func nearestDistanceText(_ meters: Double) -> String {
        if meters < 100.0 {
            return "Odległość: \(Int(meters)) m"
        }
        return String(format: "Odległość: %.1f km", meters / 1000.0)
    }

    /// Cardinal direction text like Android `OutsideZoneContent.getCardinalDirection`.
    static func cardinalDirectionText(_ degrees: Float) -> String {
        switch degrees {
        case 337.5...360.0, 0.0...22.5: return "Północny (N)"
        case 22.5...67.5: return "Północny-Wschód (NE)"
        case 67.5...112.5: return "Wschód (E)"
        case 112.5...157.5: return "Południowy-Wschód (SE)"
        case 157.5...202.5: return "Południowy (S)"
        case 202.5...247.5: return "Południowy-Zachód (SW)"
        case 247.5...292.5: return "Zachodni (W)"
        case 292.5...337.5: return "Północny-Zachód (NW)"
        default: return "Nieznany"
        }
    }

    /// Fire risk status text mappding from Android `fireRiskStatusText`.
    static func fireRiskStatusText(_ level: Int) -> String {
        switch level {
        case 0: return "STOPNIEŃ 0 (Brak zagrożenia)"
        case 1: return "STOPNIEŃ 1 (Niskie zagrożenie)"
        case 2: return "STOPNIEŃ 2 (Średnie zagrożenie)"
        case 3: return "STOPNIEŃ 3 (BARDZO WYSOKIE)"
        case 10: return "STOPNIEŃ 0 (Brak - archiwalne offline)"
        case 11: return "STOPNIEŃ 1 (Niskie - archiwalne offline)"
        case 12: return "STOPNIEŃ 2 (Średnie - archiwalne offline)"
        case 13: return "STOPNIEŃ 3 (WYSOKIE - archiwalne offline)"
        case -2: return "Brak danych z serwisu"
        case -1: return "Brak połączenia"
        default: return "Status pożarowy: Nieznany"
        }
    }

    /// Fire risk color from Android `Theme.kt` risk level colors.
    static func fireRiskColor(_ level: Int) -> Color {
        switch level {
        case 0, 10: return ZWL.riskNone
        case 1, 11: return ZWL.riskLow
        case 2, 12: return ZWL.riskMedium
        case 3, 13: return ZWL.riskHigh
        default: return ZWL.riskUnknown
        }
    }

    enum StoveRule {
        case allowed(String)
        case ban(String)
        case noData
    }

    /// Gas-stove rule derived from fire risk, like Android `InZoneContent`.
    static func stoveRule(for level: Int) -> StoveRule {
        switch level {
        case 0, 1, 2: return .allowed("DOZWOLONE")
        case 10, 11, 12: return .allowed("DOZWOLONE (dane archiwalne)")
        case 3: return .ban("BEZWZGLĘDNY ZAKAZ")
        case 13: return .ban("BEZWZGLĘDNY ZAKAZ (dane archiwalne)")
        default: return .noData
        }
    }

    static let gasStoveDisclaimer =
        "Status kuchenek wyznaczany jest wyłącznie na podstawie stopnia zagrożenia pożarowego. Aby mieć absolutną pewność, sprawdź stronę swojego nadleśnictwa."

    /// Title-cased first letter like Android `banReasonText`.
    static func banReason(_ reason: String) -> String {
        guard let first = reason.first else { return reason }
        return String(first).uppercased() + reason.dropFirst()
    }

    /// MapLibre-style fire risk levels from the shared SDK (unknown => nil).
    static func fireRiskText(_ level: Int) -> String? {
        switch level {
        case 1: return "1 - MAŁY"
        case 2: return "2 - ŚREDNI"
        case 3: return "3 - DUŻY"
        default: return nil
        }
    }

    static func bearingText(_ degrees: Float) -> String {
        let directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        let idx = Int((degrees + 22.5) / 45.0) % directions.count
        return directions[idx]
    }
}