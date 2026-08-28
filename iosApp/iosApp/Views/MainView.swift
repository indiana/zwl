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
        .tint(viewModel.currentInZone != nil ? ZWL.forestGreenAccent : ZWL.yellowPrimary)
        .sheet(isPresented: isZoneSheetPresented) {
            if let zone = viewModel.selectedZone {
                ZoneDetailView(zone: zone)
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
        if let inZone = viewModel.currentInZone {
            InZoneView(
                district: inZone.forestDistrict,
                fireRisk: viewModel.fireRiskLevel,
                ban: viewModel.activeForestBan,
                onBanTap: { viewModel.openActiveBan() },
                onDistrictTap: { viewModel.selectZone(named: inZone.forestDistrict) },
                isDownloading: viewModel.isDownloading,
                downloadProgress: viewModel.downloadProgress,
                downloadText: viewModel.downloadStatusText,
                downloadFinished: viewModel.downloadFinished,
                onDownload: { viewModel.downloadVisibleArea() }
            )
        } else if let outside = viewModel.currentOutsideZone {
            OutsideZoneView(
                nearestDistrict: outside.nearestDistrict,
                distanceMeters: outside.distanceMeters,
                bearingDegrees: outside.bearingDegrees,
                azimuth: viewModel.azimuth,
                ban: viewModel.activeForestBan,
                onBanTap: { viewModel.openActiveBan() },
                onDistrictTap: { viewModel.selectZone(named: outside.nearestDistrict) },
                isDownloading: viewModel.isDownloading,
                downloadProgress: viewModel.downloadProgress,
                downloadText: viewModel.downloadStatusText,
                downloadFinished: viewModel.downloadFinished,
                onDownload: { viewModel.downloadVisibleArea() }
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
                topBar
                Spacer()
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
            onTapZone: { viewModel.selectZone(named: $0) },
            onTapBan: { viewModel.selectBan(byRemoteId: $0) },
            onTapPoi: { viewModel.selectPoi(named: $0) },
            onTapBackground: { viewModel.clearSelection() },
            onVisibleRegionChange: { viewModel.visibleRegion = $0 }
        )
        .ignoresSafeArea(edges: .top)
    }

    private var topBar: some View {
        HStack(spacing: 8) {
            Text("Zanocuj w Lesie")
                .font(.headline)
                .padding(.horizontal, 4)
            Spacer()
            toggleChip("Zakazy", isOn: $viewModel.showBans)
            toggleChip("Wiaty", isOn: $viewModel.showShelters)
            toggleChip("Ogniska", isOn: $viewModel.showFireplaces)
            toggleChip("Inne", isOn: $viewModel.showOthers)
            Button(action: { viewModel.refreshAllData() }) {
                Image(systemName: "arrow.clockwise")
            }
            .buttonStyle(.bordered)
        }
        .padding(8)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding([.horizontal, .top])
    }

    private func toggleChip(_ label: String, isOn: Binding<Bool>) -> some View {
        Button(action: { isOn.wrappedValue.toggle() }) {
            Text(label)
                .font(.caption)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(isOn.wrappedValue ? Color.accentColor.opacity(0.2) : Color.clear)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
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