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
                    if let coords = CoordinateParser.shared.parse(input: pasteInput) {
                        viewModel.openPointFromPaste(latitude: coords.latitude, longitude: coords.longitude)
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
                     : "Wklej współrzędne lub tekst, który je zawiera, np. 52.123456, 21.123456.")
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
}