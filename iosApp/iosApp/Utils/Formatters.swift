import Foundation

enum Formatters {

    /// Formats `distanceMeters` like the Android app: m for < 1000, km above.
    static func distanceText(_ meters: Double) -> String {
        if meters < 1000 {
            return String(format: "%.0f m", meters)
        }
        return String(format: "%.1f km", meters / 1000.0)
    }

    static func bearingText(_ degrees: Float) -> String {
        let directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        let idx = Int((degrees + 22.5) / 45.0) % directions.count
        return directions[idx]
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
}