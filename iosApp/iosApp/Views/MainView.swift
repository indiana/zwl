import SwiftUI
import shared

struct MainView: View {
    @ObservedObject var viewModel: MainViewModel

    var body: some View {
        ZStack {
            switch viewModel.phase {
            case .loading:
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Ładowanie danych...")
                        .foregroundColor(.secondary)
                }
            case .error(let message):
                VStack(spacing: 12) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 40))
                        .foregroundColor(.orange)
                    Text(message)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    Button("Spróbuj ponownie", action: { viewModel.refreshAllData() })
                        .buttonStyle(.borderedProminent)
                }
            case .permissionsRequired:
                PermissionsView(onRequestPermission: {
                    viewModel.requestPermission()
                })
            case .ready:
                mapContent
            }
        }
        .task {
            viewModel.start()
        }
    }

    // MARK: - Map content

    private var mapContent: some View {
        ZStack(alignment: .bottom) {
            map
            VStack {
                topBar
                Spacer()
                bottomCard
            }
        }
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

    @ViewBuilder
    private var bottomCard: some View {
        if let inZone = viewModel.currentInZone {
            InZoneView(
                district: inZone.forestDistrict,
                fireRisk: viewModel.fireRiskLevel,
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
                isDownloading: viewModel.isDownloading,
                downloadProgress: viewModel.downloadProgress,
                downloadText: viewModel.downloadStatusText,
                downloadFinished: viewModel.downloadFinished,
                onDownload: { viewModel.downloadVisibleArea() }
            )
        } else {
            Text("Szukam lokalizacji...")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .padding()
                .background(.thinMaterial)
                .clipShape(Capsule())
                .padding([.horizontal, .bottom])
        }
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