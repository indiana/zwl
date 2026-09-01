import SwiftUI
import shared

/// Properties of a saved point (opened from a long press on the list row):
/// rename (text alert), share (system share sheet) and delete (confirmation)
/// — Android `SavedPointPropertiesCard` parity.
struct SavedPointPropertiesView: View {
    let point: SavedPoint
    @ObservedObject var viewModel: MainViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var isRenamePromptPresented = false
    @State private var isDeleteConfirmationPresented = false

    init(point: SavedPoint, viewModel: MainViewModel) {
        self.point = point
        self.viewModel = viewModel
        _name = State(initialValue: point.name)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                headerRow

                coordinatesSubcard

                Spacer(minLength: 4)

                HStack(spacing: 12) {
                    Button(action: { isRenamePromptPresented = true }) {
                        Label("Zmień nazwę", systemImage: "pencil")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    ShareLink(item: shareText) {
                        Label("Podziel się", systemImage: "square.and.arrow.up")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    Button(action: { isDeleteConfirmationPresented = true }) {
                        Label("Usuń", systemImage: "trash")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                }
            }
            .padding(16)
            .background(Color(.systemBackground))
            .navigationTitle("Właściwości punktu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        viewModel.clearSavedPointProperties()
                        dismiss()
                    }
                }
            }
            .alert("Zmień nazwę", isPresented: $isRenamePromptPresented) {
                TextField("Nazwa punktu", text: $name)
                Button("Zapisz") {
                    viewModel.renameSavedPoint(point, to: name)
                }
                Button("Anuluj", role: .cancel) {}
            }
            .alert("Usunąć punkt?", isPresented: $isDeleteConfirmationPresented) {
                Button("Usuń", role: .destructive) {
                    viewModel.deleteSavedPoint(point)
                }
                Button("Anuluj", role: .cancel) {}
            } message: {
                Text("Punkt „\(point.name)” zostanie trwale usunięty.")
            }
        }
    }

    private var headerRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(point.name.isEmpty ? "Bez nazwy" : point.name)
                    .font(.system(size: 18, weight: .bold))
                Text("Zapisywany punkt")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
    }

    private var coordinatesSubcard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("WSPÓŁRZĘDNE")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(Formatters.coordinateText(latitude: point.latitude, longitude: point.longitude))
                .font(.system(size: 13))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var shareText: String {
        let coords = Formatters.coordinateText(latitude: point.latitude, longitude: point.longitude)
        var text = "Legalny Bushcraft — punkt"
        if !point.name.isEmpty { text += ": \(point.name)" }
        text += "\n\(coords)\nMożesz wkleić te współrzędne w aplikacji, aby otworzyć punkt."
        return text
    }
}