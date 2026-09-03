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

/// A map coordinate awaiting the user's confirmation to save it. Filled by a
/// long-press on the map or a `zwl://point` deep link (Android parity).
struct PendingPoint: Identifiable {
    enum Source: Equatable {
        case longPress
        case link
        case paste
    }

    let id = UUID()
    let source: Source
    var name: String
    let latitude: Double
    let longitude: Double
    // Zone status computed offline via the shared SpatialEngine (Android
    // parity): nil until the async lookup finishes.
    var status: LocationStatus?
    var ban: ForestBan?
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

    // Layer toggles (persisted across launches like Android's
    // SharedPreferences `zwl_map_settings`; they reset to defaults only on
    // first launch).
    @Published var showBans: Bool = true {
        didSet { UserDefaults.standard.set(showBans, forKey: Self.keyShowBans) }
    }
    @Published var showShelters: Bool = true {
        didSet { UserDefaults.standard.set(showShelters, forKey: Self.keyShowShelters) }
    }
    @Published var showRest: Bool = true {
        didSet { UserDefaults.standard.set(showRest, forKey: Self.keyShowRest) }
    }
    @Published var showFireplaces: Bool = true {
        didSet { UserDefaults.standard.set(showFireplaces, forKey: Self.keyShowFireplaces) }
    }
    @Published var showOthers: Bool = true {
        didSet { UserDefaults.standard.set(showOthers, forKey: Self.keyShowOthers) }
    }
    @Published var showAccommodation: Bool = true {
        didSet { UserDefaults.standard.set(showAccommodation, forKey: Self.keyShowAccommodation) }
    }
    @Published var showViewpoints: Bool = true {
        didSet { UserDefaults.standard.set(showViewpoints, forKey: Self.keyShowViewpoints) }
    }
    @Published var showParking: Bool = true {
        didSet { UserDefaults.standard.set(showParking, forKey: Self.keyShowParking) }
    }
    @Published var showEducation: Bool = true {
        didSet { UserDefaults.standard.set(showEducation, forKey: Self.keyShowEducation) }
    }

    /// Whether the map follows the user's live location (`MLNMapView`
    /// `userTrackingMode == .follow`). Owned here (not MainView's @AppStorage)
    /// so selecting a saved point can drop follow and keep the camera on the
    /// point (Android parity: the camera doesn't snap back to the user). The
    /// user can re-enable it with the settings-panel toggle.
    @Published var followsUser: Bool = true {
        didSet { UserDefaults.standard.set(followsUser, forKey: Self.keyFollowsUser) }
    }

    // Selections
    @Published var selectedZone: Zone?
    @Published var selectedBan: ForestBan?
    @Published var selectedPoi: Poi?
    @Published var selectedPoiDistanceMeters: Double?

    // Saved points (Android parity: magenta markers, long-press to save,
    // shared via `zwl://point` deep links).
    @Published var savedPoints: [SavedPoint] = []
    @Published var savedPointsGeoJson: String = ""
    @Published var pendingPoint: PendingPoint?
    @Published var showSavedPointList = false
    @Published var selectedSavedPoint: SavedPoint?
    // Layer-visible toggles (Android parity). showOwnPoints resets to true on
    // launch like Android; the POI/ban toggles above persist.
    @Published var showOwnPoints = true
    @Published var showLayersOverlay = false
    // Increment to ask the map to center on a saved point (zoom 15).
    @Published var centerSavedPointSignal: Int = 0
    private(set) var centerSavedPointLatitude: Double?
    private(set) var centerSavedPointLongitude: Double?

    // User location. Published as ONE coordinate pair per GPS fix instead of
    // separate lat/lon: every @Published write re-evaluates MainView's body,
    // so two writes per ~1Hz fix doubled the SwiftUI churn that trips the
    // iPad CPU watchdog. Coordinates are degenerate only between fixes.
    @Published var userCoordinate: CLLocationCoordinate2D?
    var userLatitude: Double? { userCoordinate?.latitude }
    var userLongitude: Double? { userCoordinate?.longitude }
    // Not `@Published`: it's written on every pan/zoom frame via
    // regionDidChange, so publishing would force a SwiftUI re-render (and an
    // updateUIView) per frame and make map scrolling stutter.
    var visibleRegion: MapRegion?

    // Increment to ask the map to re-center on the user position
    @Published var recenterSignal: Int = 0

    // Transient "Oczekiwanie na sygnał GPS..." pill (Android toast parity)
    // shown when the re-center button is tapped before the first GPS fix.
    @Published var gpsWaitingMessageVisible = false

    // Compass heading (degrees, 0 = north)
    @Published var azimuth: Float = 0

    // Active forest ban covering the current position (nil if none)
    @Published var activeForestBan: ForestBan?

    // Fire risk
    @Published var fireRiskLevel: Int = -1

    // Zone detail sheet state (distance + fire risk + stove rule + BDL forest
    // stand card — Android parity).
    @Published var selectedZoneDistanceMeters: Double?
    @Published var selectedZoneFireRiskLevel: Int?
    @Published var isLoadingZoneFireRisk = false
    @Published var selectedZoneForestStand: ForestStandSummary?
    @Published var isLoadingZoneForestStand = false

    // Offline download
    @Published var isDownloading = false
    @Published var downloadProgress: Float = 0
    @Published var downloadStatusText = ""
    @Published var downloadFinished = false
    @Published var downloadErrorText: String? = nil
    // Coalescing state for progress publishes (see applyDownloadProgress).
    private var lastDownloadProgressShown: Float = -1
    private var lastDownloadTextShown = ""
    private var lastDownloadTextAt = Date.distantPast

    // Downloaded offline areas (Android "Pobrane obszary" overlay parity).
    // Records feed the management list; tile sources feed MapLibre (one
    // `mbtiles://` source per area). `offlineSourcesSignal` bumps whenever
    // the sources change so the map re-applies them.
    @Published var offlineAreaRecords: [DownloadedArea] = []
    @Published var offlineTileSources: [OfflineTileArea] = []
    @Published var offlineSourcesSignal = 0
    @Published var showOfflineAreas = false
    // Non-nil -> MainView shows a modal explanation why the download cannot
    // start (oversized view). The status card is too easy to miss.
    @Published var downloadBlockedMessage: String? = nil
    // Increment to fly the map camera to a downloaded area's bounding box.
    @Published var focusAreaSignal = 0
    private(set) var focusAreaRegion: MapRegion?

    let app: ForestApp
    private let locationManager = CLLocationManager()
    private let pathMonitor = NWPathMonitor()
    private static let keyShowBans = "mapSettings.showBans"
    private static let keyShowShelters = "mapSettings.showShelters"
    private static let keyShowRest = "mapSettings.showRest"
    private static let keyShowFireplaces = "mapSettings.showFireplaces"
    private static let keyShowOthers = "mapSettings.showOthers"
    private static let keyShowAccommodation = "mapSettings.showAccommodation"
    private static let keyShowViewpoints = "mapSettings.showViewpoints"
    private static let keyShowParking = "mapSettings.showParking"
    private static let keyShowEducation = "mapSettings.showEducation"
    private static let keyFollowsUser = "settings.followsUser"
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
        let defaults = UserDefaults.standard
        showBans = defaults.object(forKey: Self.keyShowBans) as? Bool ?? true
        showShelters = defaults.object(forKey: Self.keyShowShelters) as? Bool ?? true
        showRest = defaults.object(forKey: Self.keyShowRest) as? Bool ?? true
        showFireplaces = defaults.object(forKey: Self.keyShowFireplaces) as? Bool ?? true
        showOthers = defaults.object(forKey: Self.keyShowOthers) as? Bool ?? true
        showAccommodation = defaults.object(forKey: Self.keyShowAccommodation) as? Bool ?? true
        showViewpoints = defaults.object(forKey: Self.keyShowViewpoints) as? Bool ?? true
        showParking = defaults.object(forKey: Self.keyShowParking) as? Bool ?? true
        showEducation = defaults.object(forKey: Self.keyShowEducation) as? Bool ?? true
        followsUser = defaults.object(forKey: Self.keyFollowsUser) as? Bool ?? true
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
                // Janitor inside initialize() may have removed legacy/orphan
                // files — load the management list only after it settles.
                await self.reloadOfflineAreas()
                await self.refreshMapData()
                if !ok && self.app.cachedZones().isEmpty {
                    self.phase = .error("Błąd synchronizacji danych. Sprawdź połączenie internetowe.")
                    return
                }
                await self.computeLocationStatus()
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
                await self.computeLocationStatus()
                self.phase = .ready
            } catch {
                self.phase = .error("Błąd odświeżania danych: \(error.localizedDescription)")
            }
        }
    }

    func refreshMapData() async {
        guard let zones = try? await app.zonesGeoJson(),
              let bans = try? await app.bansGeoJson(),
              let pois = try? await app.poisGeoJson() else { return }
        zonesGeoJson = zones
        bansGeoJson = bans
        poisGeoJson = pois
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

    func computeLocationStatus() async {
        guard let lat = userLatitude, let lon = userLongitude else {
            if activeForestBan != nil { activeForestBan = nil }
            return
        }
        guard let newStatus = try? await app.checkLocation(latitude: lat, longitude: lon) else { return }
        let newBan = try? await app.checkForestBan(latitude: lat, longitude: lon)
        let district = (newStatus as? LocationStatusInZone)?.forestDistrict
        if district != lastInZoneDistrict {
            lastInZoneDistrict = district
            if fireRiskLevel != -1 { fireRiskLevel = -1 }
            if district != nil {
                Task { await self.refreshFireRiskIfNeeded() }
            }
        }
        // Only publish when the value actually changed (each @Published write
        // re-evaluates the whole MainView body; on a stationary fix this used
        // to republish 3 identical values ~1Hz).
        if !Self.locationStatusEquals(newStatus, locationStatus) {
            locationStatus = newStatus
        }
        if !Self.forestBanEquals(newBan, activeForestBan) {
            activeForestBan = newBan
        }
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

    /// Structural equality for the bridged `LocationStatus`: SKIE/Kotlin data
    /// classes don't conform to Swift `Equatable`, so compare the fields that
    /// drive the UI. Used to skip redundant `@Published` writes on stationary
    /// GPS fixes (each write re-evaluates the whole MainView body).
    private static func locationStatusEquals(_ a: LocationStatus?, _ b: LocationStatus?) -> Bool {
        switch (a, b) {
        case (nil, nil):
            return true
        case let (aIn as LocationStatusInZone, bIn as LocationStatusInZone):
            return aIn.forestDistrict == bIn.forestDistrict
        case let (aOut as LocationStatusOutsideZone, bOut as LocationStatusOutsideZone):
            return aOut.nearestDistrict == bOut.nearestDistrict
                && aOut.distanceMeters == bOut.distanceMeters
                && aOut.bearingDegrees == bOut.bearingDegrees
        case (is LocationStatusEmptyData, is LocationStatusEmptyData):
            return true
        default:
            return false
        }
    }

    /// The ban is compared by its stable remote id (a bare `==` is unavailable
    /// on the bridged Kotlin class).
    private static func forestBanEquals(_ a: ForestBan?, _ b: ForestBan?) -> Bool {
        switch (a, b) {
        case (nil, nil):
            return true
        case let (aBan?, bBan?):
            return aBan.remoteId == bBan.remoteId
        default:
            return false
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
        let district = zone.forestDistrict
        if let userLat = userLatitude, let userLng = userLongitude {
            if currentInZone?.forestDistrict == district {
                selectedZoneDistanceMeters = 0
            } else {
                let geojson = zonesGeoJson
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    guard let first = await Self.firstShellCoordinateAsync(of: district, in: geojson) else { return }
                    self.selectedZoneDistanceMeters = Self.equirectDistance(
                        lat1: userLat, lon1: userLng,
                        lat2: first.0, lon2: first.1
                    )
                }
            }
        }

        selectForestStand(for: zone)

        let geojson = zonesGeoJson
        isLoadingZoneFireRisk = true
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            guard let first = await Self.firstShellCoordinateAsync(of: district, in: geojson) else {
                self.isLoadingZoneFireRisk = false
                return
            }
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
    /// GeoJSON the map uses (avoids pulling WKT parsing into Swift). Runs off
    /// the main actor; see `firstShellCoordinateAsync`.
    private nonisolated static func firstShellCoordinate(of district: String, in geojson: String) -> (Double, Double)? {
        guard let data = geojson.data(using: .utf8),
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

    private static func firstShellCoordinateAsync(of district: String, in geojson: String) async -> (Double, Double)? {
        await Task.detached(priority: .userInitiated) {
            Self.firstShellCoordinate(of: district, in: geojson)
        }.value
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

    // MARK: - Saved points

    /// Handles an incoming `zwl://point?lat=..&lng=..[&name=..]` deep link
    /// (custom URL scheme; Android intent-filter parity).
    func openPointFromLink(_ url: URL) {
        guard url.scheme?.lowercased() == "zwl", url.host?.lowercased() == "point" else { return }
        guard let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        let lat = comps.queryItems?.first(where: { $0.name == "lat" }).flatMap { Double($0.value ?? "") }
        let lng = comps.queryItems?.first(where: { $0.name == "lng" }).flatMap { Double($0.value ?? "") }
        guard let lat = lat, let lng = lng else { return }
        let name = comps.queryItems?.first(where: { $0.name == "name" })?.value ?? ""
        openPointFromLink(latitude: lat, longitude: lng, name: name)
    }

    func onLongPressPoint(latitude: Double, longitude: Double) {
        setPendingPoint(latitude: latitude, longitude: longitude, name: "", source: .longPress)
    }

    func openPointFromLink(latitude: Double, longitude: Double, name: String?) {
        setPendingPoint(latitude: latitude, longitude: longitude, name: name ?? "", source: .link)
    }

    /// Pasted coordinates (Android `openPointFromPaste` parity).
    func openPointFromPaste(latitude: Double, longitude: Double) {
        setPendingPoint(latitude: latitude, longitude: longitude, name: "", source: .paste)
    }

    /// Fills the pending-point card and asynchronously computes its zone status
    /// / forest ban off the shared spatial engine (Android parity). These
    /// events are rare (a tap / link), so no `@Published` throttling needed —
    /// but the Kotlin callbacks still hop through `Task { @MainActor }`.
    private func setPendingPoint(latitude: Double, longitude: Double, name: String, source: PendingPoint.Source) {
        pendingPoint = PendingPoint(source: source, name: name, latitude: latitude, longitude: longitude)
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let status = try? await self.app.checkLocation(latitude: latitude, longitude: longitude)
            let ban = try? await self.app.checkForestBan(latitude: latitude, longitude: longitude)
            self.pendingPoint?.status = status
            self.pendingPoint?.ban = ban
        }
    }

    func savePendingPoint(name: String) {
        guard var point = pendingPoint else { return }
        point.name = name
        pendingPoint = point
        let trimmed = name.isEmpty ? "Zapisany punkt" : name
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            _ = try? await self.app.savePoint(name: trimmed, latitude: point.latitude, longitude: point.longitude)
            self.pendingPoint = nil
            await self.loadSavedPointData()
        }
    }

    func clearPendingPoint() {
        pendingPoint = nil
    }

    func loadSavedPointData() async {
        let points = (try? await app.savedPoints()).map { Array($0) } ?? []
        savedPoints = points
        savedPointsGeoJson = (try? await app.savedPointsGeoJson()) ?? ""
    }

    func openSavedPointList() {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            await self.loadSavedPointData()
            self.showSavedPointList = true
        }
    }

    func closeSavedPointList() {
        showSavedPointList = false
    }

    func openLayersOverlay() {
        showLayersOverlay = true
    }

    func closeLayersOverlay() {
        showLayersOverlay = false
    }

    /// Short tap on a list row: center the camera on the point (map stays
    /// visible under the closed list). Also drops follow-the-user so the
    /// camera stays parked on the chosen point instead of snapping back on the
    /// next GPS fix (Android parity: it doesn't follow). Re-enable follow via
    /// the settings toggle or a fresh app kill.
    func selectSavedPoint(_ point: SavedPoint) {
        centerSavedPointLatitude = point.latitude
        centerSavedPointLongitude = point.longitude
        centerSavedPointSignal += 1
        followsUser = false
    }

    func openSavedPointProperties(_ point: SavedPoint) {
        selectedSavedPoint = point
    }

    func openSavedPointProperties(id: Int64) {
        guard let point = savedPoints.first(where: { $0.id == id }) else { return }
        openSavedPointProperties(point)
    }

    func clearSavedPointProperties() {
        selectedSavedPoint = nil
    }

    func renameSavedPoint(_ point: SavedPoint, to name: String) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            _ = try? await self.app.renameSavedPoint(id: point.id, name: name)
            await self.loadSavedPointData()
        }
    }

    func deleteSavedPoint(_ point: SavedPoint) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            _ = try? await self.app.deleteSavedPoint(id: point.id)
            self.selectedSavedPoint = nil
            await self.loadSavedPointData()
        }
    }

    // MARK: - Offline download

    func recenterMap() {
        // "My location" re-enables follow-the-user (e.g. after it was dropped
        // to keep the camera parked on a selected saved point) and centers on
        // the current fix.
        followsUser = true
        guard userLatitude != nil else {
            // Android shows a toast here ("Oczekiwanie na sygnał GPS...");
            // render a transient pill and hide it after ~2.5s.
            if !gpsWaitingMessageVisible {
                gpsWaitingMessageVisible = true
                Task { @MainActor [weak self] in
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    self?.gpsWaitingMessageVisible = false
                }
            }
            return
        }
        recenterSignal += 1
    }

    // MARK: - Offline areas management

    /// Reloads the downloaded-areas list + MapLibre tile sources from the
    /// shared repository. Called on startup, after each download and after
    /// every mutation (delete/rename/refresh).
    func reloadOfflineAreas() async {
        let records = (try? await app.offlineAreas()) ?? []
        let sources = records.map {
            OfflineTileArea(id: $0.id, path: app.offlineAreaFilePath(fileName: $0.fileName))
        }
        offlineAreaRecords = records
        offlineTileSources = sources
        offlineSourcesSignal += 1
    }

    func openOfflineAreas() { showOfflineAreas = true }

    func closeOfflineAreas() { showOfflineAreas = false }

    func deleteOfflineArea(_ area: DownloadedArea) {
        Task { [weak self] in
            try? await self?.app.deleteOfflineArea(id: area.id)
            await self?.reloadOfflineAreas()
        }
    }

    func deleteAllOfflineAreas() {
        Task { [weak self] in
            try? await self?.app.deleteAllOfflineAreas()
            await self?.reloadOfflineAreas()
        }
    }

    func renameOfflineArea(_ area: DownloadedArea, name: String) {
        Task { [weak self] in
            try? await self?.app.renameOfflineArea(id: area.id, name: name)
            await self?.reloadOfflineAreas()
        }
    }

    /// Re-downloads the area's bbox; progress reuses the download card.
    func refreshOfflineArea(_ area: DownloadedArea) {
        guard !isDownloading else { return }
        isDownloading = true
        downloadProgress = 0
        downloadStatusText = "Rozpoczynanie odświeżania..."
        downloadFinished = false
        downloadErrorText = nil
        lastDownloadProgressShown = -1
        lastDownloadTextShown = ""
        lastDownloadTextAt = Date.distantPast

        Task { [weak self] in
            guard let self = self else { return }
            do {
                try await self.app.refreshOfflineArea(
                    id: area.id,
                    onProgress: { [weak self] progress, text in
                        Task { @MainActor [weak self] in
                            self?.applyDownloadProgress(progress.floatValue, text: text)
                        }
                    },
                    onSuccess: { [weak self] count in
                        Task { @MainActor [weak self] in
                            self?.downloadStatusText = "Obszar odświeżony (\(count) kafelków)"
                            self?.downloadFinished = true
                        }
                    },
                    onError: { [weak self] message in
                        Task { @MainActor [weak self] in
                            self?.downloadStatusText = message
                            self?.downloadErrorText = message
                            self?.downloadFinished = true
                        }
                    }
                )
            } catch {
                self.downloadStatusText = "Błąd odświeżania: \(error.localizedDescription)"
                self.downloadErrorText = self.downloadStatusText
                self.downloadFinished = true
            }
            self.isDownloading = false
            await self.reloadOfflineAreas()
        }
    }

    /// Tap on a managed area: dismiss the list and fly the camera to its bbox.
    func focusOfflineArea(_ area: DownloadedArea) {
        focusAreaRegion = MapRegion(latSouth: area.latSouth,
                                    latNorth: area.latNorth,
                                    lonWest: area.lonWest,
                                    lonEast: area.lonEast)
        focusAreaSignal += 1
        closeOfflineAreas()
    }

    func downloadVisibleArea() {
        guard let region = visibleRegion else { return }
        downloadArea(region: region)
    }

    /// Throttled progress callback for the offline download (Android only
    /// drives its UI via a flow + throttling; Kotlin fires our callback per
    /// tile, i.e. ~10-20 Hz on device). Publishing every tile forces a SwiftUI
    /// update cycle per callback and pinned the main thread at ~80% CPU while
    /// downloading (tripped the CPU watchdog, iOS 'cpu resource' termination).
    /// Coalesce: progress on ~2% steps (forcing the final 1.0), text at most
    /// every 0.35s unless the progress stepped.
    private func applyDownloadProgress(_ progress: Float, text: String) {
        let step: Float = 0.02
        let steped = abs(progress - lastDownloadProgressShown) >= step || progress >= 0.999
        let now = Date()
        let textDue = (text != lastDownloadTextShown)
            && (now.timeIntervalSince(lastDownloadTextAt) >= 0.35 || steped)
        if steped {
            lastDownloadProgressShown = progress
            downloadProgress = progress
        }
        if textDue {
            lastDownloadTextShown = text
            lastDownloadTextAt = now
            downloadStatusText = text
        }
    }

    func downloadArea(region: MapRegion) {
        guard !isDownloading else { return }
        // Reject oversized views up front with a modal message (Android
        // parity) — the packager's error would only flash the status card.
        downloadBlockedMessage = nil
        Task { [weak self] in
            guard let self = self else { return }
            let estimated = (try? await self.app.estimateAreaTiles(
                latSouth: region.latSouth,
                latNorth: region.latNorth,
                lonWest: region.lonWest,
                lonEast: region.lonEast
            )) ?? 0
            let limit = OfflineLimits.shared.MAX_TILES
            if estimated > limit {
                self.downloadBlockedMessage =
                    "Ten widok obejmuje \(estimated) kafelków — limit to \(limit). Przybliż mapę i spróbuj ponownie."
                return
            }
            await self.startDownload(region: region)
        }
    }

    private func startDownload(region: MapRegion) async {
        isDownloading = true
        downloadProgress = 0
        downloadStatusText = "Rozpoczynanie..."
        downloadFinished = false
        downloadErrorText = nil
        lastDownloadProgressShown = -1
        lastDownloadTextShown = ""
        lastDownloadTextAt = Date.distantPast

        do {
            try await app.downloadArea(
                    latSouth: region.latSouth,
                    latNorth: region.latNorth,
                    lonWest: region.lonWest,
                    lonEast: region.lonEast,
                    onProgress: { [weak self] progress, text in
                        // Kotlin invokes these from Dispatchers.Default; hop to
                        // the main actor before touching @Published state.
                        Task { @MainActor [weak self] in
                            guard let self = self else { return }
                            self.applyDownloadProgress(progress.floatValue, text: text)
                        }
                    },
                    onSuccess: { [weak self] count in
                        Task { @MainActor [weak self] in
                            guard let self = self else { return }
                            self.downloadStatusText = "Pobrano kafelków: \(count)"
                            self.downloadFinished = true
                        }
                    },
                    onError: { [weak self] message in
                        Task { @MainActor [weak self] in
                            guard let self = self else { return }
                            self.downloadStatusText = message
                            self.downloadErrorText = message
                            self.downloadFinished = true
                        }
                    }
                )
        } catch {
            downloadStatusText = "Błąd pobierania: \(error.localizedDescription)"
            downloadErrorText = "Błąd pobierania: \(error.localizedDescription)"
            downloadFinished = true
        }
        isDownloading = false
        await reloadOfflineAreas()
    }

    // MARK: - Helpers

    /// Status as shown to the user.
    var displayInZone: LocationStatusInZone? { locationStatus as? LocationStatusInZone }
    var displayOutsideZone: LocationStatusOutsideZone? { locationStatus as? LocationStatusOutsideZone }

    var currentInZone: LocationStatusInZone? { locationStatus as? LocationStatusInZone }
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
        userCoordinate = loc.coordinate
        Task { await self.computeLocationStatus() }
        // Refresh the fire risk only while it's unresolved: each fix used to
        // spawn a redundant Task that immediately returned.
        if fireRiskLevel < 0 {
            Task { await self.refreshFireRiskIfNeeded() }
        }
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