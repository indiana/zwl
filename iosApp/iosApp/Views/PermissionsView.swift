import SwiftUI

/// Mirrors Android `PermissionsScreen.kt`: dark card over the dark forest background.
struct PermissionsView: View {
    let onRequestPermission: () -> Void

    var body: some View {
        VStack {
            Spacer()

            VStack(spacing: 0) {
                Text("Wymagane Uprawnienia Lokalizacyjne")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Text("Aplikacja \"Legalny Bushcraft\" wymaga dostępu do precyzyjnej lokalizacji GPS w celu sprawdzania czy znajdujesz się w legalnej strefie biwakowania oraz do nawigacji kompasem offline w terenie.")
                    .font(.system(size: 14))
                    .foregroundColor(ZWL.forestGreenSubtext)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.top, 12)

                Button(action: onRequestPermission) {
                    Text("Zezwól na dostęp")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(ZWL.greenPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
                .padding(.top, 24)
            }
            .padding(24)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(ZWL.darkForestSurface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(24)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ZWL.darkForestBackground.ignoresSafeArea())
    }
}