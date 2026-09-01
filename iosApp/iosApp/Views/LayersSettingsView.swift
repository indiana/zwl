import SwiftUI
import shared

/// Full-screen "Wyświetlanie na mapie" settings (Android `MapLayersOverlay`
/// parity): one toggle per layer group, own points first in owner-brand
/// magenta.
struct LayersSettingsView: View {
    @ObservedObject var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Toggle(isOn: $viewModel.showOwnPoints) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0xE91E63)).frame(width: 10, height: 10)
                        Text("Własne punkty")
                    }
                }
                Toggle(isOn: $viewModel.showBans) {
                    HStack(spacing: 10) {
                        Circle().fill(Color.red).frame(width: 10, height: 10)
                        Text("Zakazy wstępu do lasu")
                    }
                }
                Toggle(isOn: $viewModel.showAccommodation) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x1B5E20)).frame(width: 10, height: 10)
                        Text("Noclegi i biwakowanie")
                    }
                }
                Toggle(isOn: $viewModel.showRest) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x558B2F)).frame(width: 10, height: 10)
                        Text("Miejsca wypoczynku")
                    }
                }
                Toggle(isOn: $viewModel.showShelters) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x4E342E)).frame(width: 10, height: 10)
                        Text("Wiaty i schronienia")
                    }
                }
                Toggle(isOn: $viewModel.showFireplaces) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0xE65100)).frame(width: 10, height: 10)
                        Text("Miejsca na ognisko")
                    }
                }
                Toggle(isOn: $viewModel.showViewpoints) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x0097A7)).frame(width: 10, height: 10)
                        Text("Punkty widokowe i rekreacja")
                    }
                }
                Toggle(isOn: $viewModel.showParking) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x5D4037)).frame(width: 10, height: 10)
                        Text("Parkingi")
                    }
                }
                Toggle(isOn: $viewModel.showEducation) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x7B1FA2)).frame(width: 10, height: 10)
                        Text("Edukacja leśna")
                    }
                }
                Toggle(isOn: $viewModel.showOthers) {
                    HStack(spacing: 10) {
                        Circle().fill(Color(hex: 0x1976D2)).frame(width: 10, height: 10)
                        Text("Inne punkty")
                    }
                }
            }
            .navigationTitle("Wyświetlanie na mapie")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gotowe") { dismiss() }
                }
            }
        }
    }
}