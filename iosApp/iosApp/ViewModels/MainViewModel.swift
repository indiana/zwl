import Foundation
import CoreLocation
import Combine
import Network
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

    /// True while the device has no satisfying network path (airplane mode
    /// / no connectivity). Android shows a red "Tryb offline" pill on the map
    /// then; we mirror that (NWPathMonitor, updates on any path change).
    @Published var isOffline = false

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
    @Published var selectedPoiDistanceMeters: Double?

    // User location
    @Published var userLatitude: Double?
    @Published var userLongitude: Double?
    // Not `@Published`: it's written on every pan/zoom frame via
    // regionDidChange, so publishing would force a SwiftUI re-render (and an
    // updateUIView) per frame and make map scrolling stutter.
    var visibleRegion: MapRegion?

    // Increment to ask the map to re-center on the user position
    @Published var recenterSignal: Int = 0

    // Compass heading (degrees, 0 = north)
    @Published var azimuth: Float = 0

    // Active forest ban covering the current position (nil if none)
    @Published var activeForestBan: ForestBan?

    // Fire risk
    @Published var fireRiskLevel: Int = -1

    // Debug / QA overrides. `debugUiEnabled` gates all diagnostics UI
    // (status-tab flip, menu diagnostics toggles, map overlay). Kept `false`
    // for the release candidate; flip back to `true` when QA needs them again.
    var debugUiEnabled: Bool { false }
    @Published var debugInvertZone = false

    // Zone detail sheet state (distance + fire risk + stove rule + BDL forest
    // stand card — Android parity).
    @Published var selectedZoneDistanceMeters: Double?
    @Published var selectedZoneFireRiskLevel: Int?
    @Published var isLoadingZoneFireRisk = false
    @Published var selectedZoneForestStand: ForestStandSummary?
    @Published var isLoadingZoneForestStand = false

    // Live map diagnostics (overlay shown while the map overlays are being
    // debugged on device; remove once rendering is confirmed).
    @Published var mapDiagnostics: String = ""

    // Offline download
    @Published var isDownloading = false
    @Published var downloadProgress: Float = 0
    @Published var downloadStatusText = ""
    @Published var downloadFinished = false
    @Published var downloadErrorText: String? = nil

    let app: ForestApp
    private let locationManager = CLLocationManager()
    private let pathMonitor = NWPathMonitor()
    private var lastInZoneDistrict: String?
    // Throttling: GPS is 1Hz and heading can be tens of Hz; each update
    // re-renders the map on the main thread (the iPad-class bottleneck), so
    // only meaningful movement/timing changes are published.
    private var lastPublishedLocation: CLLocation?
    private var lastHeadingAt = Date.distantPast
    private var lastAzimuthValue: Float?

    init(app: ForestApp) {
        self.app = app
        super.init()
        locationManager.delegate = self
        pathMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                self?.isOffline = (path.status != .satisfied)
            }
        }
        pathMonitor.start(queue: DispatchQueue(label: "zwl.network.monitor"))
    }

    // MARK: - Startup

    func start() {
        phase = .loading
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let ok = try await self.app.initialize().boolValue
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
                _ = try await self.app.syncZones()
                _ = try await self.app.syncBans()
                _ = try await self.app.syncPois()
                try await self.app.refreshSpatialIndexes()
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
            locationManager.startUpdatingHeading()
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
        guard let lat = userLatitude, let lon = userLongitude else {
            activeForestBan = nil
            return
        }
        let newStatus = app.checkLocation(latitude: lat, longitude: lon)
        activeForestBan = app.checkForestBan(latitude: lat, longitude: lon)
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
              locationStatus is LocationStatusInZone || locationStatus is LocationStatusOutsideZone else { return }
        if fireRiskLevel >= 0 { return }
        do {
            fireRiskLevel = try await app.getFireRisk(latitude: lat, longitude: lon).intValue
        } catch {
            fireRiskLevel = -1
        }
    }

    // MARK: - Selection

    func selectZone(named name: String?) {
        guard let name = name, let zone = app.cachedZones().first(where: { $0.forestDistrict == name }) else { return }
        selectedZone = zone
        selectedBan = nil
        selectedPoi = nil
        selectedZoneDistanceMeters = nil
        selectedZoneFireRiskLevel = nil
        isLoadingZoneFireRisk = false
        selectedZoneForestStand = nil
        isLoadingZoneForestStand = false
        computeZoneDetail(for: zone)
    }

    /// Fills the zone detail sheet: distance from the user, fire risk read at
    /// the zone's first boundary coordinate, and the BDL forest-stand card
    /// (Android parity).
    private func computeZoneDetail(for zone: Zone) {
        if let userLat = userLatitude, let userLng = userLongitude {
            if currentInZone?.forestDistrict == zone.forestDistrict {
                selectedZoneDistanceMeters = 0
            } else if let first = firstShellCoordinate(of: zone.forestDistrict) {
                selectedZoneDistanceMeters = Self.equirectDistance(
                    lat1: userLat, lon1: userLng,
                    lat2: first.0, lon2: first.1
                )
            }
        }

        selectForestStand(for: zone)

        guard let first = firstShellCoordinate(of: zone.forestDistrict) else { return }
        isLoadingZoneFireRisk = true
        Task { [weak self] in
            guard let self = self else { return }
            let level = (try? await self.app.getFireRisk(latitude: first.0, longitude: first.1).intValue) ?? -1
            self.selectedZoneFireRiskLevel = level
            self.isLoadingZoneFireRisk = false
        }
    }

    /// BDL forest-stand card (Android "STRUKTURA I CHARAKTERYSTYKA DRZEWOSTANU"
    /// parity): show the cached summary immediately, refresh from the network
    /// when the cache is missing or older than the 24h TTL, then persist the
    /// fresh copy via the same ZoneRepository cache Android uses.
    private func selectForestStand(for zone: Zone) {
        let cached = app.cachedForestStand(zone: zone)
        selectedZoneForestStand = cached

        let timestamp = zone.forestStandTimestamp
        let stale: Bool
        if let ts = timestamp {
            stale = Self.currentTimeMillis() - ts.int64Value > Self.forestStandCacheTtlMillis
        } else {
            stale = true
        }
        let needRefresh = cached == nil || stale
        guard needRefresh else { return }

        isLoadingZoneForestStand = true
        Task { [weak self] in
            guard let self = self else { return }
            if let fresh = try? await self.app.getForestStand(zone: zone) {
                self.selectedZoneForestStand = fresh
                try? await self.app.cacheForestStand(
                    zone: zone,
                    summary: fresh,
                    timestamp: Self.currentTimeMillis()
                )
            }
            self.isLoadingZoneForestStand = false
        }
    }

    private static let forestStandCacheTtlMillis: Int64 = 24 * 60 * 60 * 1000

    private static func currentTimeMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Extracts the first [lng, lat] coordinate of the zone's shell from the
    /// GeoJSON the map uses (avoids pulling WKT parsing into Swift).
    private func firstShellCoordinate(of district: String) -> (Double, Double)? {
        guard let data = zonesGeoJson.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = root["features"] as? [[String: Any]] else { return nil }
        for feature in features {
            guard let props = feature["properties"] as? [String: Any],
                  props["name"] as? String == district,
                  let geometry = feature["geometry"] as? [String: Any],
                  let raw = geometry["coordinates"] as? [Any] else { continue }
            // Polygon -> [ [lng,lat], ... ] (ring); MultiPolygon -> [ [ring], ... ].
            let firstRing: [Any]
            if let polygons = raw.first as? [[[Any]]],
               let ring = polygons.first {
                firstRing = ring
            } else if let ring = raw.first as? [[Any]] {
                firstRing = ring
            } else {
                continue
            }
            guard let firstPair = firstRing.first as? [Any],
                  let lon = (firstPair.first as? NSNumber)?.doubleValue,
                  firstPair.count >= 2,
                  let lat = (firstPair[1] as? NSNumber)?.doubleValue else { continue }
            return (lat, lon)
        }
        return nil
    }

    /// Great-circle-ish distance using the equirectangular approximation
    /// (fine at walkable distances; used for the zone detail sheet only).
    private static func equirectDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let meanLat = (lat1 + lat2) / 2.0 * .pi / 180.0
        let dLat = (lat2 - lat1) * .pi / 180.0
        let dLon = (lon2 - lon1) * .pi / 180.0
        let x = dLon * 111320.0 * cos(meanLat)
        let y = dLat * 111320.0
        return sqrt(x * x + y * y)
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
        selectedPoiDistanceMeters = nil
        if let poi = selectedPoi, let userLat = userLatitude, let userLon = userLongitude {
            let userLoc = CLLocation(latitude: userLat, longitude: userLon)
            let poiLoc = CLLocation(latitude: poi.latitude, longitude: poi.longitude)
            selectedPoiDistanceMeters = userLoc.distance(from: poiLoc)
        }
    }

    func openActiveBan() {
        guard let ban = activeForestBan else { return }
        selectedBan = ban
    }

    func clearSelection() {
        selectedZone = nil
        selectedBan = nil
        selectedPoi = nil
        selectedPoiDistanceMeters = nil
    }

    // MARK: - Offline download

    func recenterMap() {
        recenterSignal += 1
    }

    /// Deletes the packed offline tile database (SQLiter keeps it under
    /// Application Support/databases/map.mbtiles on iOS). Returns whether a
    /// cache file actually existed (Android "Wyczyść cache" parity).
    func clearOfflineCache() -> Bool {
        let fm = FileManager.default
        guard let appSupport = fm.urls(for: .applicationSupportDirectory,
                                       in: .userDomainMask).first else { return false }
        let databaseURL = appSupport
            .appendingPathComponent("databases", isDirectory: true)
            .appendingPathComponent("map.mbtiles")

        var existed = false
        let candidates = [databaseURL,
                          URL(fileURLWithPath: databaseURL.path + "-wal"),
                          URL(fileURLWithPath: databaseURL.path + "-shm")]
        for url in candidates where fm.fileExists(atPath: url.path) {
            existed = true
            try? fm.removeItem(at: url)
        }
        return existed
    }

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
        downloadErrorText = nil

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
                        self?.downloadProgress = progress.floatValue
                        self?.downloadStatusText = text
                    },
                    onSuccess: { [weak self] count in
                        self?.downloadStatusText = "Pobrano kafelków: \(count)"
                        self?.downloadFinished = true
                    },
                    onError: { [weak self] message in
                        self?.downloadStatusText = message
                        self?.downloadErrorText = message
                        self?.downloadFinished = true
                    }
                )
            } catch {
                self.downloadStatusText = "Błąd pobierania: \(error.localizedDescription)"
                self.downloadErrorText = "Błąd pobierania: \(error.localizedDescription)"
                self.downloadFinished = true
            }
            self.isDownloading = false
        }
    }

    // MARK: - Helpers

    /// Debug/QA flipping of the zone classification shown in the Status tab
    /// (real GPS data is untouched; fire risk / bans are kept as-is).
    func toggleDebugInvertZone() {
        debugInvertZone.toggle()
    }

    var displayStatus: LocationStatus? {
        guard debugInvertZone else { return locationStatus }
        switch locationStatus {
        case let inZone as LocationStatusInZone:
            return LocationStatusOutsideZone(
                nearestDistrict: inZone.forestDistrict,
                distanceMeters: 8500.0,
                bearingDegrees: 0.0
            )
        case let outside as LocationStatusOutsideZone:
            return LocationStatusInZone(forestDistrict: outside.nearestDistrict)
        default:
            return locationStatus
        }
    }

    /// Status as shown to the user (respects the debug invert toggle).
    var displayInZone: LocationStatusInZone? { displayStatus as? LocationStatusInZone }
    var displayOutsideZone: LocationStatusOutsideZone? { displayStatus as? LocationStatusOutsideZone }

    var currentInZone: LocationStatusInZone? { locationStatus as? LocationStatusInZone }
    var currentOutsideZone: LocationStatusOutsideZone? { locationStatus as? LocationStatusOutsideZone }

    /// True when we have no usable fix yet (GPS locating screen).
    var isLocationEmpty: Bool {
        locationStatus == nil || locationStatus is LocationStatusEmptyData
    }
}

// MARK: - CLLocationManagerDelegate

extension MainViewModel: CLLocationManagerDelegate {

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            self.requestLocationIfNeeded()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        if let prev = lastPublishedLocation {
            let distance = loc.distance(from: prev)
            let elapsed = loc.timestamp.timeIntervalSince(prev.timestamp)
            // Skip sub-5m jitter arriving within 2s of the last published fix —
            // a stationary device is the exact case where 1Hz re-renders stall
            // the map for no reason.
            if distance < 5, elapsed < 2 { return }
        }
        lastPublishedLocation = loc
        userLatitude = loc.coordinate.latitude
        userLongitude = loc.coordinate.longitude
        computeLocationStatus()
        Task { await self.refreshFireRiskIfNeeded() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        guard newHeading.headingAccuracy >= 0 else { return }
        let trueHeading = newHeading.trueHeading
        let magneticHeading = newHeading.magneticHeading
        let value = Float(trueHeading >= 0 ? trueHeading : magneticHeading)
        let now = Date()
        // Sensor events can stream many times a second; publish at most 1Hz and
        // only when the heading actually moved by at least a degree.
        guard now.timeIntervalSince(lastHeadingAt) >= 1.0 else { return }
        if let last = lastAzimuthValue, abs(value - last) < 1 { return }
        lastHeadingAt = now
        lastAzimuthValue = value
        azimuth = value
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
    }
}