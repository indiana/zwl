import SwiftUI
import shared

struct ZoneDetailView: View {
    let zone: Zone

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Image(systemName: "tent.fill")
                        .foregroundColor(.green)
                        .font(.title)
                    VStack(alignment: .leading) {
                        Text(zone.forestDistrict)
                            .font(.headline)
                        Text("Strefa Zanocuj w Lesie")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }

                if let urlString = zone.websiteUrl,
                   let url = URL(string: urlString) {
                    Link(destination: url) {
                        Label(urlString.replacingOccurrences(of: "https://", with: ""),
                              systemImage: "globe")
                            .font(.subheadline)
                    }
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Strefa")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        dismiss()
                    }
                }
            }
        }
    }

    @Environment(\.dismiss) private var dismiss
}