import SwiftUI
import shared

/// Full-screen "Zapisane punkty" list over the map (Android overlay parity;
/// opened from the hamburger menu, NOT a third tab). Short tap centers the
/// camera on the point; long press opens Properties; swipe-to-delete (with
/// confirmation). "Zaznacz" enters a multi-select mode to delete many at once.
struct SavedPointListView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var isPastePromptPresented = false
    @State private var pasteInput = ""
    @State private var pasteError = false

    @State private var editMode: EditMode = .inactive
    @State private var selection = Set<Int64>()

    @State private var isDeleteConfirmPresented = false
    @State private var singleDeleteCandidate: SavedPoint? = nil

    var body: some View {
        NavigationStack {
            List(selection: $selection) {
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
                        ForEach(viewModel.savedPoints, id: \.id) { point in
                            row(for: point)
                                .tag(point.id)
                        }
                    }
                }
            }
            .environment(\.editMode, $editMode)
            .listStyle(.insetGrouped)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
            .navigationTitle(editMode.isEditing ? "\(selection.count) zaznaczonych" : "Zapisane punkty")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if !editMode.isEditing {
                        Button {
                            viewModel.closeSavedPointList()
                            dismiss()
                        } label: {
                            Image(systemName: "chevron.backward")
                                .fontWeight(.semibold)
                        }
                        .accessibilityLabel("Wstecz")
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if editMode.isEditing {
                        Button("Gotowe") {
                            editMode = .inactive
                            selection.removeAll()
                        }
                    } else {
                        Button("Zaznacz") {
                            editMode = .active
                        }
                        .disabled(viewModel.savedPoints.isEmpty)
                    }
                }
                if editMode.isEditing {
                    ToolbarItem(placement: .bottomBar) {
                        HStack {
                            Button("Zaznacz wszystkie") {
                                if selection.count == viewModel.savedPoints.count {
                                    selection.removeAll()
                                } else {
                                    selection = Set(viewModel.savedPoints.map { $0.id })
                                }
                            }
                            Spacer()
                            Button(role: .destructive) {
                                singleDeleteCandidate = nil
                                isDeleteConfirmPresented = true
                            } label: {
                                Label("Usuń (\(selection.count))", systemImage: "trash")
                            }
                            .disabled(selection.isEmpty)
                        }
                    }
                }
            }
            .alert(deleteConfirmTitle, isPresented: $isDeleteConfirmPresented) {
                Button("Usuń", role: .destructive) { performDelete() }
                Button("Anuluj", role: .cancel) { singleDeleteCandidate = nil }
            } message: {
                Text(deleteConfirmMessage)
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
            guard !editMode.isEditing else { return }
            viewModel.selectSavedPoint(point)
            viewModel.closeSavedPointList()
            dismiss()
        }
        .contextMenu {
            if !editMode.isEditing {
                Button {
                    viewModel.openSavedPointProperties(point)
                } label: {
                    Label("Właściwości", systemImage: "slider.horizontal.3")
                }
            }
        }
        .swipeActions {
            Button(role: .destructive) {
                singleDeleteCandidate = point
                isDeleteConfirmPresented = true
            } label: {
                Label("Usuń", systemImage: "trash")
            }
        }
    }

    private var deleteConfirmTitle: String {
        "Usunąć zapisane punkty?"
    }

    private var deleteConfirmMessage: String {
        if let point = singleDeleteCandidate {
            return "Czy na pewno chcesz usunąć punkt „\(point.name)”?"
        }
        let count = selection.count
        if count >= viewModel.savedPoints.count && count > 0 {
            return "Usunięte zostaną wszystkie punkty (\(count)). Tej operacji nie można cofnąć."
        }
        if count == 1 {
            return "Usunięty zostanie 1 punkt. Tej operacji nie można cofnąć."
        }
        let word: String
        if count % 10 >= 2 && count % 10 <= 4 && !(count % 100 >= 12 && count % 100 <= 14) {
            word = "punkty"
            return "Usunięte zostaną \(count) \(word). Tej operacji nie można cofnąć."
        }
        word = "punktów"
        return "Usunięte zostanie \(count) \(word). Tej operacji nie można cofnąć."
    }

    private func performDelete() {
        if let point = singleDeleteCandidate {
            singleDeleteCandidate = nil
            viewModel.deleteSavedPoint(point)
        } else {
            let ids = Array(selection)
            selection.removeAll()
            editMode = .inactive
            viewModel.deleteSavedPoints(ids)
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
