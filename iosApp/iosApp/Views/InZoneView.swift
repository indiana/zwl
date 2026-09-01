import SwiftUI
import shared

/// Full-screen in-zone status, mirroring Android `InZoneContent.kt`.
struct InZoneView: View {
    let district: String
    let fireRisk: Int
    let ban: ForestBan?
    let onBanTap: () -> Void
    let onDistrictTap: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if let ban {
                    ForestBanAlertBanner(forestBan: ban, onTap: onBanTap)
                        .padding(.bottom, 16)
                }

                VStack(spacing: 24) {
                    ZStack {
                        Circle()
                            .fill(ZWL.greenPrimary.opacity(0.2))
                            .frame(width: 100, height: 100)
                            .overlay(Circle().stroke(ZWL.forestGreenAccent, lineWidth: 3))
                        Image(systemName: "checkmark")
                            .font(.system(size: 48, weight: .bold))
                            .foregroundColor(ZWL.forestGreenAccent)
                    }

                    Text("Jesteś w strefie\nprogramu \"Zanocuj w Lesie\"")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)

                    Button(action: onDistrictTap) {
                        HStack(spacing: 8) {
                            Text(district)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(ZWL.forestGreenText)
                                .multilineTextAlignment(.center)
                            Image(systemName: "info.circle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(ZWL.forestGreenAccent)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(ZWL.greenPrimary.opacity(0.15))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(ZWL.forestGreenAccent, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 16)

                fireRiskCard
                    .padding(.vertical, 24)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(ZWL.greenBackground.ignoresSafeArea())
    }

    private var fireRiskCard: some View {
        VStack(spacing: 0) {
            Text("Zagrożenie pożarowe w lasach")
                .font(.system(size: 14))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)

            Text(Formatters.fireRiskStatusText(fireRisk))
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Formatters.fireRiskColor(fireRisk))
                .multilineTextAlignment(.center)
                .padding(.top, 4)
                .padding(.bottom, 16)

            Rectangle()
                .fill(ZWL.darkGray)
                .frame(height: 1)

            Text("Używanie kuchenek gazowych")
                .font(.system(size: 14))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.top, 16)
                .padding(.bottom, 8)

            stoveBadge
                .padding(.bottom, 12)

            Text(Formatters.gasStoveDisclaimer)
                .font(.system(size: 11))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
        .padding(20)
        .background(ZWL.greenSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var stoveBadge: some View {
        switch Formatters.stoveRule(for: fireRisk) {
        case .allowed(let text):
            Text(text)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(ZWL.forestGreenAccent)
                .padding(.horizontal, 24)
                .padding(.vertical, 10)
                .background(ZWL.greenPrimary.opacity(0.15))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ZWL.greenPrimary, lineWidth: 1))
        case .ban(let text):
            PulsingBanBadge(text: text)
        case .noData:
            Text("BRAK DANYCH\nnie używaj kuchenek gazowych\nsprawdź komunikat w nadleśnictwie")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(ZWL.errorRedAccent)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(ZWL.errorRedButton.opacity(0.15))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(ZWL.errorRedButton, lineWidth: 1))
        }
    }
}

struct PulsingBanBadge: View {
    let text: String
    var fontSize: CGFloat = 18

    @State private var pulse = false

    var body: some View {
        Text(text)
            .font(.system(size: fontSize, weight: .bold))
            .foregroundColor(ZWL.errorRedAccent)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 24)
            .padding(.vertical, 10)
            .background(ZWL.errorRedButton.opacity(0.2 * (pulse ? 1.0 : 0.6)))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(ZWL.errorRedButton.opacity(pulse ? 1.0 : 0.6), lineWidth: 2)
            )
            .onAppear {
                withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
    }
}