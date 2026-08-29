import SwiftUI
import shared

struct MainView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        ZStack {
            switch viewModel.phase {
            case .loading:
                LoadingView()
            case .error(let message):
                ErrorView(message: message) {
                    viewModel.refreshAllData()
                }
            case .permissionsRequired:
                PermissionsView(onRequestPermission: {
                    viewModel.requestPermission()
                })
            case .ready:
                mainContent
            }
        }
        .task {
            viewModel.start()
        }
    }

    // MARK: - Main tab content (Android parity: Status + Mapa)

    private var mainContent: some View {
        TabView {
            statusTab
                .tabItem { Label("Status", systemImage: "info.circle") }
            mapTab
                .tabItem { Label("Mapa", systemImage: "map") }
        }
        .tint(viewModel.displayInZone != nil ? ZWL.forestGreenAccent : ZWL.yellowPrimary)
        .sheet(isPresented: isZoneSheetPresented) {
            if let zone = viewModel.selectedZone {
                ZoneDetailView(zone: zone,
                               distanceMeters: viewModel.selectedZoneDistanceMeters,
                               fireRiskLevel: viewModel.selectedZoneFireRiskLevel,
                               isLoadingFireRisk: viewModel.isLoadingZoneFireRisk)
                    .presentationDetents([.medium, .large])
            }
        }
        .sheet(isPresented: isBanSheetPresented) {
            if let ban = viewModel.selectedBan {
                ForestBanDetailView(ban: ban)
                    .presentationDetents([.large])
            }
        }
        .sheet(isPresented: isPoiSheetPresented) {
            if let poi = viewModel.selectedPoi {
                PoiDetailView(poi: poi)
                    .presentationDetents([.medium])
            }
        }
    }

    @ViewBuilder
    private var statusTab: some View {
        if let inZone = viewModel.displayInZone {
            InZoneView(
                district: inZone.forestDistrict,
                fireRisk: viewModel.fireRiskLevel,
                ban: viewModel.activeForestBan,
                onBanTap: { viewModel.openActiveBan() },
                onDistrictTap: { viewModel.selectZone(named: inZone.forestDistrict) },
                onDebugToggle: viewModel.debugUiEnabled ? { viewModel.toggleDebugInvertZone() } : nil
            )
        } else if let outside = viewModel.displayOutsideZone {
            OutsideZoneView(
                nearestDistrict: outside.nearestDistrict,
                distanceMeters: outside.distanceMeters,
                bearingDegrees: outside.bearingDegrees,
                azimuth: viewModel.azimuth,
                ban: viewModel.activeForestBan,
                onBanTap: { viewModel.openActiveBan() },
                onDistrictTap: { viewModel.selectZone(named: outside.nearestDistrict) },
                onDebugToggle: viewModel.debugUiEnabled ? { viewModel.toggleDebugInvertZone() } : nil
            )
        } else {
            GpsLocatingView()
        }
    }

    // MARK: - Map content

    private var mapTab: some View {
        ZStack {
            map

            VStack {
                if viewModel.isDownloading || !viewModel.downloadStatusText.isEmpty {
                    MapDownloadCard(text: viewModel.downloadStatusText,
                                    progress: viewModel.downloadProgress,
                                    isDownloading: viewModel.isDownloading)
                        .padding([.leading, .top], 16)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack {
                HStack(spacing: 8) {
                    Spacer()
                    myLocationButton
                    settingsMenuButton
                }
                .padding(.trailing, 16)
                .padding(.top, 8)
                Spacer()
            }

            if DebugMapOverlay.isEnabled && !viewModel.mapDiagnostics.isEmpty {
                VStack {
                    Spacer()
                    Text(viewModel.mapDiagnostics)
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.white)
                        .lineSpacing(2)
                        .padding(6)
                        .background(.black.opacity(0.6))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .padding([.leading, .bottom], 16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var map: some View {
        MapView(
            zonesJson: viewModel.zonesGeoJson,
            bansJson: viewModel.bansGeoJson,
            poisJson: viewModel.poisGeoJson,
            showBans: viewModel.showBans,
            showShelters: viewModel.showShelters,
            showFireplaces: viewModel.showFireplaces,
            showOthers: viewModel.showOthers,
            userLatitude: viewModel.userLatitude,
            userLongitude: viewModel.userLongitude,
            recenterSignal: viewModel.recenterSignal,
            onTapZone: { viewModel.selectZone(named: $0) },
            onTapBan: { viewModel.selectBan(byRemoteId: $0) },
            onTapPoi: { viewModel.selectPoi(named: $0) },
            onTapBackground: { viewModel.clearSelection() },
            onVisibleRegionChange: { viewModel.visibleRegion = $0 },
            onDiagnostics: { viewModel.mapDiagnostics = $0 }
        )
        .ignoresSafeArea(edges: .top)
    }

    /// Dropdown with layer toggles + offline download (Android settings dropdown parity).
    private var settingsMenuButton: some View {
        Menu {
            Text("Wyświetlaj na mapie")
                .font(.caption)
                .foregroundColor(.secondary)

            Button { viewModel.showBans.toggle() } label: {
                menuCheckLabel("Zakazy wstępu do lasu", checked: viewModel.showBans)
            }
            Button { viewModel.showShelters.toggle() } label: {
                menuCheckLabel("Wiaty", checked: viewModel.showShelters)
            }
            Button { viewModel.showFireplaces.toggle() } label: {
                menuCheckLabel("Ogniska", checked: viewModel.showFireplaces)
            }
            Button { viewModel.showOthers.toggle() } label: {
                menuCheckLabel("Inne", checked: viewModel.showOthers)
            }

            Divider()

            Button { viewModel.downloadVisibleArea() } label: {
                Label("Pobierz obszar offline", systemImage: "arrow.down.circle")
            }
            .disabled(viewModel.isDownloading)

            if viewModel.debugUiEnabled {
                Divider()

                Text("Tryb testowy")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button { viewModel.toggleDebugInvertZone() } label: {
                    Label("Odwróć status: strefa / poza strefą",
                          systemImage: viewModel.debugInvertZone
                            ? "arrow.left.arrow.right.circle.fill"
                            : "arrow.left.arrow.right.circle")
                }
            }
        } label: {
            Image(systemName: "slider.horizontal.3")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .foregroundColor(.primary)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
    }

    private func menuCheckLabel(_ title: String, checked: Bool) -> some View {
        Label(title, systemImage: checked ? "checkmark.circle.fill" : "circle")
    }

    private var myLocationButton: some View {
        Button(action: { viewModel.recenterMap() }) {
            Image(systemName: "location.fill")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .foregroundColor(.blue)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
        .disabled(viewModel.userLatitude == nil)
    }

    // MARK: - Sheet bindings

    private var isZoneSheetPresented: Binding<Bool> {
        Binding(get: { viewModel.selectedZone != nil },
                set: { if !$0 { viewModel.clearSelection() } })
    }

    private var isBanSheetPresented: Binding<Bool> {
        Binding(get: { viewModel.selectedBan != nil },
                set: { if !$0 { viewModel.clearSelection() } })
    }

    private var isPoiSheetPresented: Binding<Bool> {
        Binding(get: { viewModel.selectedPoi != nil },
                set: { if !$0 { viewModel.clearSelection() } })
    }
}

// MARK: - Debug overlay (QA builds only)

enum DebugMapOverlay {
    /// Kept `true` while iterating on the iOS map via TestFlight. Flip to
    /// `false` before shipping to the App Store (TestFlight builds are Release,
    /// so `#if DEBUG` cannot gate this).
    static let isEnabled = true
}

// MARK: - Loading / Error states (Android parity)

struct LoadingView: View {
    var body: some View {
        VStack(spacing: 0) {
            ProgressView()
                .progressViewStyle(.circular)
                .tint(ZWL.forestGreenAccent)
                .scaleEffect(1.6)

            Text("Legalny Bushcraft")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(ZWL.forestGreenAccent)
                .multilineTextAlignment(.center)
                .padding(.top, 24)

            Text("Inicjalizacja silnika przestrzennego i lokalizacji...")
                .font(.system(size: 14))
                .foregroundColor(ZWL.forestGreenText)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ZWL.darkForestBackground.ignoresSafeArea())
    }
}

struct ErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("Wystąpił błąd")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(ZWL.errorRedAccent)
                .multilineTextAlignment(.center)

            Text(message)
                .font(.system(size: 15))
                .foregroundColor(ZWL.errorRedText)
                .multilineTextAlignment(.center)
                .lineSpacing(6)
                .padding(.top, 16)

            Button(action: onRetry) {
                Text("Spróbuj ponownie")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(ZWL.errorRedButton)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
            .padding(.top, 32)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ZWL.errorDarkBackground.ignoresSafeArea())
    }
}