import SwiftUI
import shared

struct ForestBanDetailView: View {
    let ban: ForestBan

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                            .font(.title)
                        VStack(alignment: .leading) {
                            Text(ban.reason)
                                .font(.headline)
                            Text(ban.forestDistrictName)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }

                    if !ban.description.isEmpty {
                        Text(ban.description)
                            .font(.body)
                    }

                    Divider()
                    detailRow("Nadleśnictwo", ban.forestDistrictName)
                    if let code = ban.forestDistrictCode {
                        detailRow("Kod nadleśnictwa", code)
                    }
                    if let rdlp = ban.rdlpName {
                        detailRow("RDLP", rdlp)
                    }
                    if let forestry = ban.forestryName {
                        detailRow("Leśnictwo", forestry)
                    }
                    if let address = ban.forestAddress {
                        detailRow("Adres leśny", address)
                    }
                    if let compartment = ban.compartmentCode {
                        detailRow("Oddział", compartment)
                    }
                    if let start = ban.startDate {
                        detailRow("Data obowiązywania od", start)
                    }
                    if let end = ban.endDate {
                        detailRow("do", end)
                    }
                    if let area = ban.areaSqMeters {
                        detailRow("Powierzchnia", String(format: "%.2f m²", area))
                    }
                }
                .padding()
            }
            .navigationTitle("Zakaz wstępu")
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

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 130, alignment: .leading)
            Text(value)
                .font(.body)
            Spacer()
        }
    }

    @Environment(\.dismiss) private var dismiss
}