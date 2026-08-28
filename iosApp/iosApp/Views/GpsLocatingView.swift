import SwiftUI

/// Mirrors Android `MainScreen` EmptyData branch while the first GPS fix arrives.
struct GpsLocatingView: View {
    var body: some View {
        VStack(spacing: 0) {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(ZWL.forestGreenAccent)
                .scaleEffect(1.4)

            Text("Ustalanie lokalizacji GPS...")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(ZWL.forestGreenAccent)
                .multilineTextAlignment(.center)
                .padding(.top, 16)

            Text("Aplikacja oczekuje na pierwsze współrzędne z Twojego urządzenia. Upewnij się, że funkcja lokalizacji (GPS) jest włączona.")
                .font(.system(size: 13))
                .foregroundColor(ZWL.forestGreenText)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.top, 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ZWL.darkForestBackground.ignoresSafeArea())
    }
}