import SwiftUI
import shared

/// "Pobrane obszary" management list (Android `OfflineAreasScreen` parity):
/// tap flies the camera to the area, rows offer rename / refresh / delete,
/// the toolbar offers delete-all. Presented as a sheet from the map menu.
struct OfflineAreasView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var areaToDelete: DownloadedArea?
    @State private var confirmDeleteAll = false
    @State private var areaToRename: DownloadedArea?
    @State private var renameInput = ""

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.offlineAreaRecords.isEmpty {
                    emptyState
                } else {
                    areaList
                }
            }
            .navigationTitle("Pobrane obszary")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Zamknij") {
                        viewModel.closeOfflineAreas()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Usuń wszystkie", role: .destructive) {
                        confirmDeleteAll = true
                    }
                    .disabled(viewModel.offlineAreaRecords.isEmpty || viewModel.isDownloading)
                }
            }
            .confirmationDialog(
                "Usunąć obszar?",
                isPresented: Binding(get: { areaToDelete != nil },
                                     set: { if !$0 { areaToDelete = nil } }),
                titleVisibility: .visible
            ) {
                Button("Usuń", role: .destructive) {
                    if let area = areaToDelete {
                        viewModel.deleteOfflineArea(area)
                    }
                    areaToDelete = nil
                }
                Button("Anuluj", role: .cancel) { areaToDelete = nil }
            } message: {
                Text("„\(areaToDelete?.name ?? "")” zostanie usunięty z urządzenia.")
            }
            .confirmationDialog("Usunąć wszystkie obszary?", isPresented: $confirmDeleteAll, titleVisibility: .visible) {
                Button("Usuń wszystkie", role: .destructive) {
                    viewModel.deleteAllOfflineAreas()
                }
                Button("Anuluj", role: .cancel) {}
            } message: {
                Text("Wszystkie pobrane mapy offline zostaną usunięte z urządzenia.")
            }
            .alert("Zmień nazwę obszaru", isPresented: Binding(get: { areaToRename != nil },
                                                               set: { if !$0 { areaToRename = nil } })) {
                TextField("Nazwa", text: $renameInput)
                Button("Zapisz") {
                    if let area = areaToRename {
                        let trimmed = renameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            viewModel.renameOfflineArea(area, name: trimmed)
                        }
                    }
                    areaToRename = nil
                }
                Button("Anuluj", role: .cancel) { areaToRename = nil }
            }
        }
    }

    private var areaList: some View {
        List {
            Section {
                Text(headerSummary)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Section {
                ForEach(Array(viewModel.offlineAreaRecords.enumerated()), id: \.element.id) { _, area in
                    row(for: area)
                }
            }
            if viewModel.isDownloading {
                Section {
                    Text("Trwa pobieranie — zarządzanie zablokowane do końca pobierania.")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func row(for area: DownloadedArea) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(area.name)
                .font(.system(size: 16, weight: .semibold))
            Text("\(ageLabel(for: area)) · zoom \(area.minZoom)–\(area.maxZoom)")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
            Text("\(area.tileCount) kafelków · \(sizeLabel(for: area))")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            viewModel.focusOfflineArea(area)
            dismiss()
        }
        .contextMenu {
            Button {
                renameInput = area.name
                areaToRename = area
            } label: {
                Label("Zmień nazwę", systemImage: "pencil")
            }
            Button {
                viewModel.refreshOfflineArea(area)
            } label: {
                Label("Odśwież", systemImage: "arrow.clockwise")
            }
            .disabled(viewModel.isDownloading || viewModel.isOffline)
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                areaToDelete = area
            } label: {
                Label("Usuń", systemImage: "trash")
            }
            Button {
                renameInput = area.name
                areaToRename = area
            } label: {
                Label("Nazwa", systemImage: "pencil")
            }
            .tint(.blue)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "square.stack.3d.up")
                .font(.system(size: 34))
                .foregroundColor(.secondary)
            Text("Brak pobranych obszarów")
                .font(.system(size: 16, weight: .semibold))
            Text("Otwórz menu mapy i wybierz „Pobierz obszar offline” w miejscu, które chcesz mieć offline.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var headerSummary: String {
        let count = viewModel.offlineAreaRecords.count
        let noun: String
        switch count {
        case 1: noun = "obszar"
        case 2...4: noun = "obszary"
        default: noun = "obszarów"
        }
        let total = viewModel.offlineAreaRecords.reduce(Int64(0)) { $0 + $1.fileSizeBytes }
        return "\(count) \(noun) · \(Self.formatBytes(total))"
    }

    private func ageLabel(for area: DownloadedArea) -> String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let offset = Int(TimeZone.current.secondsFromGMT(for: Date()) / 60)
        return OfflineAreaNames.shared.ageLabel(
            downloadedAtMillis: area.downloadedAt,
            nowMillis: now,
            offsetMinutes: Int32(offset)
        )
    }

    private func sizeLabel(for area: DownloadedArea) -> String {
        Self.formatBytes(area.fileSizeBytes)
    }

    private static func formatBytes(_ bytes: Int64) -> String {
        let kb = Double(bytes) / 1024.0
        if kb < 1024 { return String(format: "%.0f kB", kb) }
        if kb < 1024 * 1024 { return String(format: "%.1f MB", kb / 1024) }
        return String(format: "%.2f GB", kb / 1024 / 1024)
    }
}
