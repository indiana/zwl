import SwiftUI

struct PermissionsView: View {
    let onRequestPermission: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "location.slash.fill")
                .font(.system(size: 56))
                .foregroundColor(.orange)
            Text("Brak dostępu do lokalizacji")
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text("Aby sprawdzić, czy znajdujesz się w strefie Zanocuj w Lesie, aplikacja potrzebuje dostępu do Twojej lokalizacji.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button(action: onRequestPermission) {
                Label("Przyznaj dostęp", systemImage: "location")
                    .font(.headline)
            }
            .buttonStyle(.borderedProminent)
            .padding(.top)
            Spacer()
        }
        .padding()
    }
}