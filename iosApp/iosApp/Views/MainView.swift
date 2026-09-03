import SwiftUI
import shared

struct MainView: View {
    @ObservedObject var viewModel: MainViewModel
    @State private var isSettingsOpen = false
    // followsUser lives on the view model (selecting a saved point turns it
    // off so the camera stays on the point; "my location" re-enables it).
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
        .sheet(isPresented: isPendingPointSheetPresented) {
            if let point = viewModel.pendingPoint {
                PointDetailView(
                    point: point,
                    onSave: { name in viewModel.savePendingPoint(name: name) },
                    shareLink: pendingPointShareLink,
                    onClose: { viewModel.clearPendingPoint() }
                )
                .presentationDetents([.medium, .large])
            }
        }
        .sheet(isPresented: isSavedPointListPresented) {
            SavedPointListView(viewModel: viewModel)
                .presentationDetents([.large])
        }
        .sheet(isPresented: isSavedPointPropertiesPresented) {
            if let point = viewModel.selectedSavedPoint {
                SavedPointPropertiesView(point: point, viewModel: viewModel)
                    .presentationDetents([.medium])
            }
        }
        .sheet(isPresented: isLayersSettingsPresented) {
            LayersSettingsView(viewModel: viewModel)
                .presentationDetents([.large])
        }
        .sheet(isPresented: Binding(
            get: { viewModel.showOfflineAreas },
            set: { if !$0 { viewModel.closeOfflineAreas() } }
        )) {
            OfflineAreasView(viewModel: viewModel)
        }
        .alert("Obszar za duży", isPresented: Binding(
            get: { viewModel.downloadBlockedMessage != nil },
            set: { if !$0 { viewModel.downloadBlockedMessage = nil } }
        )) {
            Button("OK", role: .cancel) { viewModel.downloadBlockedMessage = nil }
        } message: {
            Text(viewModel.downloadBlockedMessage ?? "")
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
                onDistrictTap: { viewModel.selectZone(named: inZone.forestDistrict) }
            )
        } else if let outside = viewModel.displayOutsideZone {
            OutsideZoneView(
                nearestDistrict: outside.nearestDistrict,
                distanceMeters: outside.distanceMeters,
                bearingDegrees: outside.bearingDegrees,
                azimuth: viewModel.azimuth,
                ban: viewModel.activeForestBan,
                onBanTap: { viewModel.openActiveBan() },
                onDistrictTap: { viewModel.selectZone(named: outside.nearestDistrict) }
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
        }
    }

    private var map: some View {
        MapView(
            zonesJson: viewModel.zonesGeoJson,
            bansJson: viewModel.bansGeoJson,
            poisJson: viewModel.poisGeoJson,
            showBans: viewModel.showBans,
            showAccommodation: viewModel.showAccommodation,
            showRest: viewModel.showRest,
            showShelters: viewModel.showShelters,
            showFireplaces: viewModel.showFireplaces,
            showViewpoints: viewModel.showViewpoints,
            showParking: viewModel.showParking,
            showEducation: viewModel.showEducation,
            showOthers: viewModel.showOthers,
            isOffline: viewModel.isOffline,
            followsUser: viewModel.followsUser,
            userLatitude: viewModel.userLatitude,
            userLongitude: viewModel.userLongitude,
recenterSignal: viewModel.recenterSignal,
            savedPointsJson: viewModel.showOwnPoints ? viewModel.savedPointsGeoJson : "",
            pendingMarkerJson: pendingMarkerGeoJson,
            centerSavedPointLatitude: viewModel.centerSavedPointLatitude,
            centerSavedPointLongitude: viewModel.centerSavedPointLongitude,
            centerSavedPointSignal: viewModel.centerSavedPointSignal,
            offlineTileSources: viewModel.offlineTileSources,
            offlineSourcesSignal: viewModel.offlineSourcesSignal,
            focusAreaRegion: viewModel.focusAreaRegion,
            focusAreaSignal: viewModel.focusAreaSignal,
            onTapZone: { viewModel.selectZone(named: $0) },
            onTapBan: { viewModel.selectBan(byRemoteId: $0) },
            onTapPoi: { viewModel.selectPoi(named: $0) },
            onTapSavedPoint: { viewModel.openSavedPointProperties(id: $0) },
            onTapBackground: { viewModel.clearSelection() },
            onVisibleRegionChange: { viewModel.visibleRegion = $0 },
            onLongPressPoint: { lat, lng in viewModel.onLongPressPoint(latitude: lat, longitude: lng) }
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
            Image(systemName: "line.horizontal.3")
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
            Button(action: {
                isSettingsOpen = false
                viewModel.openSavedPointList()
            }) {
                Label("Zapisane punkty", systemImage: "bookmark")
            }
            .font(.system(size: 15))

            Divider()

            Button(action: {
                isSettingsOpen = false
                viewModel.openLayersOverlay()
            }) {
                Label("Wyświetlanie na mapie", systemImage: "square.3.layers.3d")
            }
            .font(.system(size: 15))

            Divider()

            Button(action: {
                // Reset the card-dismissed latch so a new download shows its
                // progress card again (it used to stay true forever after the
                // first download, silently hiding every later download's card).
                dismissDownloadCard = false
                viewModel.downloadVisibleArea()
            }) {
                Label("Pobierz obszar offline", systemImage: "arrow.down.circle")
            }
            .disabled(viewModel.isDownloading)
            .font(.system(size: 15))

            Button(action: {
                isSettingsOpen = false
                viewModel.openOfflineAreas()
            }) {
                Label("Pobrane obszary", systemImage: "square.stack.3d.up")
            }
            .font(.system(size: 15))
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

    private var isLayersSettingsPresented: Binding<Bool> {
        Binding(get: { viewModel.showLayersOverlay },
                set: { if !$0 { viewModel.closeLayersOverlay() } })
    }

    private var isPendingPointSheetPresented: Binding<Bool> {
        Binding(get: { viewModel.pendingPoint != nil },
                set: { if !$0 { viewModel.clearPendingPoint() } })
    }

    private var isSavedPointListPresented: Binding<Bool> {
        Binding(get: { viewModel.showSavedPointList },
                set: { if !$0 { viewModel.closeSavedPointList() } })
    }

    /// Dedicated sheet for a saved point's properties opened from a map tap
    /// (Android `PointDetailCard` parity). Without it the properties view only
    /// nested inside `SavedPointListView`, so a tap on the map set
    /// `selectedSavedPoint` but nothing ever appeared on screen.
    private var isSavedPointPropertiesPresented: Binding<Bool> {
        Binding(get: { viewModel.selectedSavedPoint != nil },
                set: { if !$0 { viewModel.clearSavedPointProperties() } })
    }

    /// GeoJSON for the magenta temporary marker of the pending point.
    private var pendingMarkerGeoJson: String {
        guard let point = viewModel.pendingPoint else { return "" }
        let lat = String(format: "%.6f", point.latitude)
        let lng = String(format: "%.6f", point.longitude)
        return "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[\(lng),\(lat)]}}"
    }

    /// Share text for the pending (unsaved) point — plain coordinates, since
    /// messaging apps don't linkify custom schemes (WhatsApp parity).
    private var pendingPointShareLink: String {
        guard let point = viewModel.pendingPoint else { return "" }
        let coords = Formatters.coordinateText(latitude: point.latitude, longitude: point.longitude)
        return "Legalny Bushcraft — punkt\n\(coords)\nMożesz wkleić te współrzędne w aplikacji, aby otworzyć punkt."
    }
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