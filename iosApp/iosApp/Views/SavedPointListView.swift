import SwiftUI
import shared

/// Full-screen "Zapisane punkty" list over the map (Android overlay parity;
/// opened from the hamburger menu, NOT a third tab). Short tap centers the
/// camera on the point; long press opens Properties; swipe-to-delete.
struct SavedPointListView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var isPastePromptPresented = false
    @State private var pasteInput = ""
    @State private var pasteError = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button(action: {
                        pasteInput = ""
                        pasteError = false
                        isPastePromptPresented = true
                    }) {
                        Label("Otwórz punkt ze współrzędnych", systemImage: "plus.circle")
                    }
                }

                if viewModel.savedPoints.isEmpty {
                    Section {
                        emptyState
                    }
                } else {
                    Section {
                        ForEach(Array(viewModel.savedPoints.enumerated()), id: \.offset) { _, point in
                            row(for: point)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Zapisane punkty")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        viewModel.closeSavedPointList()
                        dismiss()
                    }
                }
            }
            .alert("Otwórz punkt ze współrzędnych", isPresented: $isPastePromptPresented) {
                TextField("Szerokość, Długość", text: $pasteInput)
                Button("Otwórz punkt") {
                    if let coords = Self.parseCoordinates(pasteInput) {
                        viewModel.openPointFromPaste(latitude: coords.lat, longitude: coords.lng)
                        viewModel.closeSavedPointList()
                        dismiss()
                    } else {
                        pasteError = true
                        // Re-present so the user sees the error and can retry.
                        DispatchQueue.main.async { isPastePromptPresented = true }
                    }
                }
                Button("Anuluj", role: .cancel) {}
            } message: {
                Text(pasteError
                     ? "Nie rozpoznano współrzędnych."
                     : "Wklej współrzędne, np. 52.123456, 21.123456.")
            }
        }
    }

    private func row(for point: SavedPoint) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(point.name.isEmpty ? "Bez nazwy" : point.name)
                .font(.system(size: 16, weight: .semibold))
            Text(Formatters.coordinateText(latitude: point.latitude, longitude: point.longitude))
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            viewModel.selectSavedPoint(point)
            viewModel.closeSavedPointList()
            dismiss()
        }
        .contextMenu {
            Button {
                viewModel.openSavedPointProperties(point)
            } label: {
                Label("Właściwości", systemImage: "slider.horizontal.3")
            }
        }
        .swipeActions {
            Button(role: .destructive) {
                viewModel.deleteSavedPoint(point)
            } label: {
                Label("Usuń", systemImage: "trash")
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "bookmark")
                .font(.system(size: 34))
                .foregroundColor(.secondary)
            Text("Brak zapisanych punktów")
                .font(.system(size: 16, weight: .semibold))
            Text("Długo przytrzymaj mapę, aby dodać punkt.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
    }

    /// Parses `lat, lng` with either decimal point or Polish decimal comma,
    /// e.g. "52.123456, 21.123456" or "52,123456; 21,123456" (Android
    /// `parseCoordinates` parity).
    private static func parseCoordinates(_ input: String) -> (lat: Double, lng: Double)? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let regex = try? NSRegularExpression(
            pattern: #"\s*([-]?\d+(?:[.,]\d+)?)\s*[,;\s]\s*([-]?\d+(?:[.,]\d+)?)\s*"#
        ) else { return nil }
        let nsFull = NSRange(location: 0, length: (trimmed as NSString).length)
        guard let match = regex.firstMatch(in: trimmed, range: nsFull),
              match.range == nsFull else { return nil }
        func number(_ at: Int) -> Double? {
            guard let range = Range(match.range(at: at), in: trimmed) else { return nil }
            return Double(String(trimmed[range]).replacingOccurrences(of: ",", with: "."))
        }
        guard let lat = number(1), let lng = number(2) else { return nil }
        guard (-90...90).contains(lat), (-180...180).contains(lng) else { return nil }
        return (lat, lng)
    }
}