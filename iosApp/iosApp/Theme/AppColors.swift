import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}

/// Color palette mirroring the Android app (`presentation/theme/Theme.kt`).
enum ZWL {
    // Green (in-zone)
    static let greenPrimary = Color(hex: 0x2E7D32)
    static let greenSecondary = Color(hex: 0x1B5E20)
    static let greenBackground = Color(hex: 0x0C190D)
    static let greenSurface = Color(hex: 0x162D18)
    static let greenText = Color(hex: 0xE8F5E9)

    // Amber (out-zone)
    static let yellowPrimary = Color(hex: 0xFBC02D)
    static let yellowSecondary = Color(hex: 0xF57F17)
    static let yellowBackground = Color(hex: 0x131313)
    static let yellowSurface = Color(hex: 0x1A1A1A)
    static let yellowText = Color(hex: 0xFFFDE7)

    // Common dark forest (loading, permissions)
    static let darkForestBackground = Color(hex: 0x0F1B10)
    static let darkForestSurface = Color(hex: 0x192F1B)
    static let forestGreenAccent = Color(hex: 0x81C784)
    static let forestGreenText = Color(hex: 0xA5D6A7)
    static let forestGreenSubtext = Color(hex: 0xC8E6C9)

    // Errors
    static let errorDarkBackground = Color(hex: 0x261010)
    static let errorRedAccent = Color(hex: 0xEF5350)
    static let errorRedText = Color(hex: 0xFFCDD2)
    static let errorRedButton = Color(hex: 0xC62828)

    // Fire risk levels
    static let riskNone = Color(hex: 0x81C784)
    static let riskLow = Color(hex: 0xFFF176)
    static let riskMedium = Color(hex: 0xFFB74D)
    static let riskHigh = Color(hex: 0xE57373)
    static let riskUnknown = Color(hex: 0xB0BEC5)

    // Additional
    static let amberAccent = Color(hex: 0xFFB300)

    // Neutral grays (mirror Compose Color.DarkGray / Color.LightGray)
    static let darkGray = Color(hex: 0x444444)
    static let lightGray = Color(hex: 0xD3D3D3)
}