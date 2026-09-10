import SwiftUI

/// "O aplikacji" (Android `AboutScreen` parity): legal disclaimer, data
/// sources with tappable links, privacy note and the app version footer.
struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    private let bdlPortalUrl = URL(string: "https://www.bdl.lasy.gov.pl/portal/")!
    private let iblFireUrl = URL(string: "https://bazapozarow.ibles.pl/")!
    private let zanocujWLeseUrl = URL(string: "https://www.lasy.gov.pl/pl/turystyka/program-zanocuj-w-lesie")!

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Aplikacja „Legalny Bushcraft” jest niezależnym narzędziem stworzonym przez podmiot prywatny. Nie reprezentuje ona, nie jest powiązana, autoryzowana ani wspierana przez żadną instytucję rządową, państwową ani publiczną, w tym przez Państwowe Gospodarstwo Leśne Lasy Państwowe, Ministerstwo Klimatu i Środowiska czy Bank Danych o Lasach (BDL).")
                        .font(.system(size: 13))
                    Text("Prezentowane dane mają charakter wyłącznie informacyjny i pomocniczy. Przed wyruszeniem w teren samodzielnie zweryfikuj informacje i przestrzegaj aktualnych regulaminów lokalnych nadleśnictw.")
                        .font(.system(size: 13))
                } header: {
                    sectionHeader("Zastrzeżenie prawne", systemImage: "info.circle")
                }

                Section {
                    Text("Aplikacja pobiera i prezentuje informacje pochodzące z oficjalnych, publicznych i ogólnodostępnych źródeł:")
                        .font(.system(size: 13))
                    sourceLink(label: "Bank Danych o Lasach (BDL)", url: bdlPortalUrl)
                    sourceLink(label: "Zagrożenie pożarowe (IBL)", url: iblFireUrl)
                    sourceLink(label: "Program „Zanocuj w lesie”", url: zanocujWLeseUrl)
                    Text("Dane geometryczne stref programu „Zanocuj w lesie” oraz lokalizacje infrastruktury turystycznej pochodzą z serwisu Banku Danych o Lasach. Informacje o stopniu zagrożenia pożarowego opracowywane są przez Instytut Badawczy Leśnictwa.")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                } header: {
                    sectionHeader("Źródła danych rządowych", systemImage: "globe")
                }

                Section {
                    Text("Twoja lokalizacja służy wyłącznie do sprawdzenia stref i jest przetwarzana lokalnie na urządzeniu. Aby pokazać zagrożenie pożarowe, aplikacja wysyła anonimowe zapytanie z Twoimi współrzędnymi do publicznego API BDL — bez tworzenia profili i bez zapisu historii lokalizacji. Aplikacja nie zawiera reklam ani systemów analitycznych.")
                        .font(.system(size: 13))
                } header: {
                    sectionHeader("Prywatność", systemImage: "lock")
                }

                Section {
                    Text("Legalny Bushcraft • wersja \(appVersion)")
                        .font(.system(size: 12))
                        .foregroundColor(ZWL.forestGreenAccent)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .listStyle(.insetGrouped)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
            .navigationTitle("O aplikacji")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.backward")
                            .fontWeight(.semibold)
                    }
                    .accessibilityLabel("Wstecz")
                }
            }
        }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    private func sectionHeader(_ title: String, systemImage: String) -> some View {
        Label(title, systemImage: systemImage)
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(.primary)
            .textCase(nil)
    }

    private func sourceLink(label: String, url: URL) -> some View {
        Button {
            UIApplication.shared.open(url)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.system(size: 13))
                    .foregroundColor(.primary)
                Text(url.absoluteString)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(ZWL.forestGreenAccent)
                    .underline()
            }
        }
    }
}
