import Foundation
import CoreLocation
import Combine
import shared
import MapLibre

struct MapRegion {
    let latSouth: Double
    let latNorth: Double
    let lonWest: Double
    let lonEast: Double
}

@MainActor
final class MainViewModel: NSObject, ObservableObject {

    enum AppPhase: Equatable {
        case loading
        case error(String)
        case permissionsRequired
        case ready
    }

    @Published var phase: AppPhase = .loading
    @Published var locationStatus: LocationStatus?

    // Map data (GeoJSON strings produced by shared)
    @Published var zonesGeoJson: String = ""
    @Published var bansGeoJson: String = ""
    @Published var poisGeoJson: String = ""

    // Layer toggles
    @Published var showBans: Bool = true
    @Published var showShelters: Bool = true
    @Published var showFireplaces: Bool = true
    @Published var showOthers: Bool = true

    // Selections
    @Published var selectedZone: Zone?
    @Published var selectedBan: ForestBan?
    @Published var selectedPoi: Poi?

    // User location
    @Published var userLatitude: Double?
    @Published var userLongitude: Double?
    @Published var visibleRegion: MapRegion?

    // Fire risk
    @Published var fireRiskLevel: Int = -1

    // Offline download
    @Published var isDownloading = false
    @Published var downloadProgress: Float = 0
    @Published var downloadStatusText = ""
    @Published var downloadFinished = false

    let app: ForestApp
    private let locationManager = CLLocationManager()
    private var lastInZoneDistrict: String?

    init(app: ForestApp) {
        self.app = app
        super.init()
        locationManager.delegate = self
    }

    // MARK: - Startup

    func start() {
        phase = .loading
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let ok = try await self.app.initialize()
                await self.refreshMapData()
                if !ok && self.app.cachedZones().isEmpty {
                    self.phase = .error("Błąd synchronizacji danych. Sprawdź połączenie internetowe.")
                    return
                }
                self.computeLocationStatus()
                self.phase = .ready
                self.requestLocationIfNeeded()
            } catch {
                self.phase = .error("Błąd inicjalizacji aplikacji: \(error.localizedDescription)")
            }
        }
    }

    func refreshAllData() {
        phase = .loading
        Task { [weak self] in
            guard let self = self else { return }
            do {
                _ = try await (self.app.syncZones() && self.app.syncBans() && self.app.syncPois())
                await self.app.refreshSpatialIndexes()
                await self.refreshMapData()
                self.computeLocationStatus()
                self.phase = .ready
            } catch {
                self.phase = .error("Błąd odświeżania danych: \(error.localizedDescription)")
            }
        }
    }

    func refreshMapData() async {
        zonesGeoJson = app.zonesGeoJson()
        bansGeoJson = app.bansGeoJson()
        poisGeoJson = app.poisGeoJson()
    }

    // MARK: - Location

    private func requestLocationIfNeeded() {
        switch locationManager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            locationManager.startUpdatingLocation()
            if phase == .permissionsRequired {
                phase = .ready
            }
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        default:
            phase = .permissionsRequired
        }
    }

    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    func computeLocationStatus() {
        guard let lat = userLatitude, let lon = userLongitude else { return }
        let newStatus = app.checkLocation(latitude: lat, longitude: lon)
        let district = (newStatus as? LocationStatusInZone)?.forestDistrict
        if district != lastInZoneDistrict {
            lastInZoneDistrict = district
            fireRiskLevel = -1
            if district != nil {
                Task { await self.refreshFireRiskIfNeeded() }
            }
        }
        locationStatus = newStatus
    }

    func refreshFireRiskIfNeeded() async {
        guard let lat = userLatitude, let lon = userLongitude,
              locationStatus is LocationStatusInZone else {
            if locationStatus is LocationStatusOutsideZone {
                do {
                    fireRiskLevel = try await app.getFireRisk(latitude: lat, longitude: lon)
                } catch {
                    fireRiskLevel = -1
                }
            }
            return
        }
        if fireRiskLevel >= 0 { return }
        do {
            fireRiskLevel = try await app.getFireRisk(latitude: lat, longitude: lon)
        } catch {
            fireRiskLevel = -1
        }
    }

    // MARK: - Selection

    func selectZone(named name: String?) {
        guard let name = name else { return }
        selectedZone = app.cachedZones().first { $0.forestDistrict == name }
        selectedBan = nil
        selectedPoi = nil
    }

    func selectBan(byRemoteId remoteId: Int64) {
        selectedBan = app.cachedBans().first { $0.remoteId == remoteId }
        selectedZone = nil
        selectedPoi = nil
    }

    func selectPoi(named name: String) {
        selectedPoi = app.cachedPois().first { $0.name == name }
        selectedZone = nil
        selectedBan = nil
    }

    func clearSelection() {
        selectedZone = nil
        selectedBan = nil
        selectedPoi = nil
    }

    // MARK: - Offline download

    func downloadVisibleArea() {
        guard let region = visibleRegion else { return }
        downloadArea(region: region)
    }

    func downloadArea(region: MapRegion) {
        guard !isDownloading else { return }
        isDownloading = true
        downloadProgress = 0
        downloadStatusText = "Rozpoczynanie..."
        downloadFinished = false

        Task { [weak self] in
            guard let self = self else { return }
            do {
                try await self.app.downloadArea(
                    latSouth: region.latSouth,
                    latNorth: region.latNorth,
                    lonWest: region.lonWest,
                    lonEast: region.lonEast,
                    minZoom: 10,
                    maxZoom: 16,
                    maxTiles: 500,
                    onProgress: { [weak self] progress, text in
                        self?.downloadProgress = progress
                        self?.downloadStatusText = text
                    },
                    onSuccess: { [weak self] count in
                        self?.downloadStatusText = "Pobrano kafelków: \(count)"
                        self?.downloadFinished = true
                    },
                    onError: { [weak self] message in
                        self?.downloadStatusText = message
                        self?.downloadFinished = true
                    }
                )
            } catch {
                self.downloadStatusText = "Błąd pobierania: \(error.localizedDescription)"
                self.downloadFinished = true
            }
            self.isDownloading = false
        }
    }

    // MARK: - Helpers

    var currentInZone: LocationStatusInZone? { locationStatus as? LocationStatusInZone }
    var currentOutsideZone: LocationStatusOutsideZone? { locationStatus as? LocationStatusOutsideZone }
}

// MARK: - CLLocationManagerDelegate

extension MainViewModel: CLLocationManagerDelegate {

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        requestLocationIfNeeded()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        userLatitude = loc.coordinate.latitude
        userLongitude = loc.coordinate.longitude
        computeLocationStatus()
        Task { await self.refreshFireRiskIfNeeded() }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
    }
}