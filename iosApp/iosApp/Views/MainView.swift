import SwiftUI
import shared

struct MainView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var isSettingsOpen = false
    @State private var cacheAlertPresented = false
    @State private var cacheAlertMessage = ""
    // Persisted so A/B configs survive a cold restart (i.e. Baza OFF measured
    // at true startup, not after a mid-session style reload). Follow/heading
    // are user preferences, the rest are diagnostics toggles.
    @AppStorage("settings.overlayEnabled") private var overlayEnabled = true
    @AppStorage("settings.vectorOverlay") private var vectorOverlay = true
    @AppStorage("settings.baseEnabled") private var baseEnabled = true
    @AppStorage("settings.showUserDot") private var showUserDot = true
    @AppStorage("settings.followsUser") private var followsUser = true
    @AppStorage("settings.showHeading") private var showHeading = true
    // Dismisses the end-of-download card (bug: it used to stay on screen
    // forever once the download finished/failed).
    @State private var dismissDownloadCard = false

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
                               app: viewModel.app,
                               distanceMeters: viewModel.selectedZoneDistanceMeters,
                               fireRiskLevel: viewModel.selectedZoneFireRiskLevel,
                               isLoadingFireRisk: viewModel.isLoadingZoneFireRisk,
                               forestStand: viewModel.selectedZoneForestStand,
                               isLoadingForestStand: viewModel.isLoadingZoneForestStand)
                    .presentationDetents([.medium, .large])
            }
        }
        .sheet(isPresented: isBanSheetPresented) {
            if let ban = viewModel.selectedBan {
                ForestBanDetailView(ban: ban, app: viewModel.app)
                    .presentationDetents([.large])
            }
        }
        .sheet(isPresented: isPoiSheetPresented) {
            if let poi = viewModel.selectedPoi {
                PoiDetailView(poi: poi, distanceMeters: viewModel.selectedPoiDistanceMeters)
                    .presentationDetents([.medium])
            }
        }
        .alert("Pamięć podręczna", isPresented: $cacheAlertPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(cacheAlertMessage)
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

            if isSettingsOpen {
                // Tap-outside-to-close layer. It sits under the panel so the
                // panel's own controls keep working; toggling a switch no
                // longer dismisses the whole menu (SwiftUI Menu did).
                Color.black.opacity(0.04)
                    .contentShape(Rectangle())
                    .onTapGesture { isSettingsOpen = false }
                    .ignoresSafeArea()
            }

            VStack {
                if (viewModel.isDownloading || !viewModel.downloadStatusText.isEmpty) && !dismissDownloadCard {
                    MapDownloadCard(text: viewModel.downloadStatusText,
                                    progress: viewModel.downloadProgress,
                                    isDownloading: viewModel.isDownloading,
                                    errorMessage: viewModel.downloadErrorText)
                        .padding([.leading, .top], 16)
                        .onTapGesture { dismissDownloadCard = true }
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if viewModel.isOffline {
                VStack {
                    HStack {
                        offlineBanner
                        Spacer()
                    }
                    Spacer()
                }
                .padding([.leading, .top], 16)
            }

            VStack {
                HStack(spacing: 8) {
                    Spacer()
                    myLocationButton
                    settingsButton
                }
                .padding(.trailing, 16)
                .padding(.top, 8)

                if isSettingsOpen {
                    settingsPanel
                        .padding(.trailing, 16)
                        .padding(.top, 12)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                Spacer()
            }

            if viewModel.gpsWaitingMessageVisible {
                gpsWaitingPill
            }

            if viewModel.debugUiEnabled && DebugMapOverlay.isEnabled && !viewModel.mapDiagnostics.isEmpty {
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
            showAccommodation: viewModel.showAccommodation,
            showShelters: viewModel.showShelters,
            showRest: viewModel.showRest,
            showFireplaces: viewModel.showFireplaces,
            showViewpoints: viewModel.showViewpoints,
            showParking: viewModel.showParking,
            showEducation: viewModel.showEducation,
            showOthers: viewModel.showOthers,
            overlayEnabled: overlayEnabled,
            vectorOverlay: vectorOverlay,
            baseEnabled: baseEnabled,
            followsUser: followsUser,
            showHeading: showHeading,
            showUserDot: showUserDot,
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

    /// Red "Tryb offline" pill (Android `MapViewContainer` parity — error-red
    /// rounded surface with an offline icon and bold label).
    private var offlineBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 16, weight: .semibold))
            Text("Tryb offline")
                .font(.system(size: 13, weight: .bold))
                .kerning(0.5)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(ZWL.errorRedButton.opacity(0.9), in: RoundedRectangle(cornerRadius: 12))
        .compositingGroup()
        .shadow(color: .black.opacity(0.2), radius: 6, y: 2)
    }

    /// Toggles the always-open settings panel (Android settings dropdown
    /// parity). The panel stays open while toggling layers; closing is done by
    /// tapping outside of it.
    private var settingsButton: some View {
        Button(action: {
            withAnimation(.easeInOut(duration: 0.15)) {
                isSettingsOpen.toggle()
            }
        }) {
            Image(systemName: "slider.horizontal.3")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .foregroundColor(isSettingsOpen ? .white : .primary)
                .background(
                    isSettingsOpen ? Color.blue : Color(.systemBackground),
                    in: RoundedRectangle(cornerRadius: 14)
                )
                .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
        }
    }

    private var settingsPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Wyświetlaj na mapie")
                .font(.caption)
                .foregroundColor(.secondary)

            Toggle(isOn: $viewModel.showBans) {
                HStack(spacing: 8) {
                    Circle().fill(Color.red).frame(width: 10, height: 10)
                    Text("Zakazy wstępu do lasu")
                }
            }
            Toggle(isOn: $viewModel.showAccommodation) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x1B5E20)).frame(width: 10, height: 10)
                    Text("Noclegi i biwakowanie")
                }
            }
            Toggle(isOn: $viewModel.showRest) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x558B2F)).frame(width: 10, height: 10)
                    Text("Miejsca wypoczynku")
                }
            }
            Toggle(isOn: $viewModel.showShelters) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x4E342E)).frame(width: 10, height: 10)
                    Text("Wiaty i schronienia")
                }
            }
            Toggle(isOn: $viewModel.showFireplaces) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0xE65100)).frame(width: 10, height: 10)
                    Text("Miejsca na ognisko")
                }
            }
            Toggle(isOn: $viewModel.showViewpoints) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x0097A7)).frame(width: 10, height: 10)
                    Text("Punkty widokowe i rekreacja")
                }
            }
            Toggle(isOn: $viewModel.showParking) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x5D4037)).frame(width: 10, height: 10)
                    Text("Parkingi")
                }
            }
            Toggle(isOn: $viewModel.showEducation) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x7B1FA2)).frame(width: 10, height: 10)
                    Text("Edukacja leśna")
                }
            }
            Toggle(isOn: $viewModel.showOthers) {
                HStack(spacing: 8) {
                    Circle().fill(Color(hex: 0x1976D2)).frame(width: 10, height: 10)
                    Text("Inne punkty")
                }
            }

            Divider()

            Button(action: { viewModel.downloadVisibleArea() }) {
                Label("Pobierz obszar offline", systemImage: "arrow.down.circle")
            }
            .disabled(viewModel.isDownloading)
            .font(.system(size: 15))

            Button(action: { clearCache() }) {
                Label("Wyczyść cache", systemImage: "trash")
            }
            .font(.system(size: 15))

            if viewModel.debugUiEnabled {
                Divider()

                Text("Diagnostyka (debug)")
                    .font(.caption)
                    .foregroundColor(.secondary)

                // Diagnostics: fades the OSM base raster off to measure how much the
                // base layer alone costs on the main thread.
                Toggle("Baza OSM (diagnoza)", isOn: $baseEnabled)

                // Diagnostics-only switch: removes the five raster overlay layers in
                // place so we can A/B whether the overlay (or the base map) is what
                // lags on device.
                Toggle("Overlay (diagnoza)", isOn: $overlayEnabled)

                // Diagnostics A/B: swap the overlay between the baked raster tiles
                // (default) and the pre-raster vector pipeline (crisp at every
                // zoom) so we can compare appearance and stall in one session.
                Toggle("Wektor (diagnoza)", isOn: $vectorOverlay)

                // Diagnostics-only: userTrackingMode .follow re-centers the camera
                // on every GPS tick, and the heading arrow rotates on every
                // magnetometer event — both force main-thread re-renders and are
                // prime suspects for the UI freezes.
                Toggle("Podążaj za lokalizacją (diagnoza)", isOn: $followsUser)
                Toggle("Strzałka kierunku (diagnoza)", isOn: $showHeading)

                // Diagnostics: the native MapLibre location dot re-renders the map
                // on every GPS tick (~1Hz) — the one continuous driver we never
                // tested except by leaving it on.
                Toggle("Kropka GPS (diagnoza)", isOn: $showUserDot)

                Divider()

                Text("Tryb testowy")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button(action: { viewModel.toggleDebugInvertZone() }) {
                    Label("Odwróć status: strefa / poza strefą",
                          systemImage: viewModel.debugInvertZone
                            ? "arrow.left.arrow.right.circle.fill"
                            : "arrow.left.arrow.right.circle")
                }
                .font(.system(size: 15))
            }
        }
        .padding(14)
        .frame(width: 280, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.black.opacity(0.08))
        )
        .shadow(color: .black.opacity(0.25), radius: 14, y: 6)
    }

    /// "Wyczyść cache" (Android parity): closes the settings panel and reports
    /// the outcome with the same wording Android uses in its toast.
    private func clearCache() {
        isSettingsOpen = false
        cacheAlertMessage = viewModel.clearOfflineCache()
            ? "Pamięć podręczna została wyczyszczona"
            : "Brak pamięci podręcznej do wyczyszczenia"
        cacheAlertPresented = true
    }

    private var myLocationButton: some View {
        Button(action: { viewModel.recenterMap() }) {
            Image(systemName: "location.fill")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .foregroundColor(.blue)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
    }

    /// Transient pill at the bottom of the map (Android toast parity) shown
    /// when "Moja lokalizacja" is tapped before the first GPS fix.
    private var gpsWaitingPill: some View {
        VStack {
            Spacer()
            Text("Oczekiwanie na sygnał GPS...")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(.black.opacity(0.75), in: Capsule())
                .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity)
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
    /// Kept `true` while iterating on the iOS map via TestFlight. Now `false`
    /// for the release candidate (the overlay also requires
    /// `MainViewModel.debugUiEnabled`, so both must be fliped to restore it).
    static let isEnabled = false
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