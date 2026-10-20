import SwiftUI
import shared

/// "Co nowego" popup (Android `WhatsNewSheet` parity): release notes for the
/// installed version with a BuyCoffee support CTA.
struct WhatsNewView: View {
    let note: ReleaseNote
    let onClose: () -> Void

    private let buyCoffeeUrl = URL(string: "https://buycoffee.to/legalny-bushcraft")!

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(note.items, id: \.self) { item in
                        Label {
                            Text(item)
                                .font(.system(size: 13))
                        } icon: {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(ZWL.forestGreenAccent)
                        }
                    }
                }
                Section {
                    VStack(spacing: 12) {
                        Text("Jeśli aplikacja jest dla Ciebie przydatna, możesz wesprzeć jej dalszy rozwój.")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity, alignment: .center)
                        Button {
                            UIApplication.shared.open(buyCoffeeUrl)
                        } label: {
                            Image("BuyCoffeeButton")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 220, height: 58)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Wesprzyj projekt w BuyCoffee")
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Co nowego w wersji \(note.version)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("OK") { onClose() }
                        .fontWeight(.semibold)
                }
            }
        }
    }
}
