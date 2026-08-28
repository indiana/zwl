import SwiftUI
import shared

/// Mirrors Android `ForestBanAlertBanner` (InZoneContent.kt): red alert card shown
/// at the top of the status screens when a forest ban covers the current position.
struct ForestBanAlertBanner: View {
    let forestBan: ForestBan
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(ZWL.errorRedButton.opacity(0.3))
                        .frame(width: 40, height: 40)
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(ZWL.errorRedAccent)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("ZAKAZ WSTĘPU DO LASU")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(ZWL.errorRedAccent)
                    Text("\(Formatters.banReason(forestBan.reason)) (\(forestBan.forestDistrictName))")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 4)

                Image(systemName: "info.circle.fill")
                    .font(.system(size: 24))
                    .foregroundColor(ZWL.errorRedAccent)
            }
            .padding(12)
            .background(ZWL.errorDarkBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(ZWL.errorRedAccent, lineWidth: 1.5)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}