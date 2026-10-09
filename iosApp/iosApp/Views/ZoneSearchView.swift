import SwiftUI
import shared

/// Full-screen "Szukaj strefy" search over the map (Android `ZoneSearchScreen`
/// parity): type a fragment of a nadleśnictwo name, sort A–Z or by distance,
/// tap a hit to fly the camera to the zone's bounding box.
struct ZoneSearchView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchField
                Divider()
                sortBar
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                countLabel
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                content
            }
            .navigationTitle("Szukaj strefy")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        viewModel.closeZoneSearch()
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.backward")
                            .fontWeight(.semibold)
                    }
                    .accessibilityLabel("Wstecz")
                }
            }
        }
        .onAppear {
            query = ""
            viewModel.setZoneSearchQuery("")
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            TextField("Nazwa nadleśnictwa…", text: $query)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
            if !query.isEmpty {
                Button {
                    query = ""
                    viewModel.setZoneSearchQuery("")
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
                .accessibilityLabel("Wyczyść")
            }
        }
        .padding(10)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .onChange(of: query) { newValue in
            viewModel.setZoneSearchQuery(newValue)
        }
    }

    private var sortBar: some View {
        HStack(spacing: 8) {
            sortChip(title: "A–Z", isSelected: viewModel.zoneSearchSortMode == .alphabetical) {
                viewModel.setZoneSearchSort(.alphabetical)
            }
            sortChip(title: "Odległość",
                     isSelected: viewModel.zoneSearchSortMode == .distance,
                     isEnabled: viewModel.zoneSearchHasLocation) {
                viewModel.setZoneSearchSort(.distance)
            }
            Spacer()
        }
    }

    private func sortChip(title: String,
                          isSelected: Bool,
                          isEnabled: Bool = true,
                          action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground),
                            in: Capsule())
                .overlay(
                    Capsule().stroke(isSelected ? Color.accentColor : Color.gray.opacity(0.4),
                                     lineWidth: isSelected ? 1.5 : 1)
                )
        }
        .foregroundColor(isSelected ? .accentColor : (isEnabled ? .primary : .secondary))
        .disabled(!isEnabled)
    }

    @ViewBuilder
    private var countLabel: some View {
        if !viewModel.zoneSearchResults.isEmpty {
            Text(zoneCountText(viewModel.zoneSearchResults.count))
                .font(.system(size: 12))
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.zoneSearchResults.isEmpty {
            emptyState
        } else {
            List(viewModel.zoneSearchResults, id: \.zone.id) { result in
                row(for: result)
            }
            .listStyle(.plain)
        }
    }

    private func row(for result: ZoneSearchResult) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            highlightedName(result.zone.forestDistrict, spans: result.matchSpans)
                .font(.system(size: 16, weight: .semibold))
            if let distance = result.distanceMeters {
                Text("Odległość: \(Formatters.distanceText(distance.doubleValue))")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            viewModel.focusZone(result.bounds)
            dismiss()
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 34))
                .foregroundColor(.secondary)
            Text(query.isEmpty ? "Brak stref" : "Brak wyników")
                .font(.system(size: 16, weight: .semibold))
            Text(query.isEmpty
                 ? "Baza stref jest pusta — uruchom synchronizację danych."
                 : "Żadna strefa nie zawiera „\(query)” w nazwie.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .frame(maxHeight: .infinity)
    }

    /// Bolds every matched token (offsets come from the shared normalize pass,
    /// which is 1:1 in UTF-16, so NSString indexing matches Kotlin exactly).
    private func highlightedName(_ name: String, spans: [ZoneMatchSpan]) -> Text {
        guard !spans.isEmpty else { return Text(verbatim: name) }
        let ns = name as NSString
        let sorted = spans
            .map { (Int($0.start), Int($0.end)) }
            .filter { $0.0 >= 0 && $0.1 > $0.0 && $0.1 <= ns.length }
            .sorted { $0.0 < $1.0 }
        var result = Text(verbatim: "")
        var cursor = 0
        for (start, end) in sorted {
            if start < cursor { continue }
            if start > cursor {
                result = result + Text(verbatim: ns.substring(with: NSRange(location: cursor, length: start - cursor)))
            }
            let match = ns.substring(with: NSRange(location: start, length: end - start))
            result = result + Text(verbatim: match)
                .fontWeight(.bold)
                .foregroundColor(.accentColor)
            cursor = end
        }
        if cursor < ns.length {
            result = result + Text(verbatim: ns.substring(from: cursor))
        }
        return result
    }

    private func zoneCountText(_ count: Int) -> String {
        let last = count % 10
        let lastTwo = count % 100
        if count == 1 { return "\(count) strefa" }
        if last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14) {
            return "\(count) strefy"
        }
        return "\(count) stref"
    }
}
