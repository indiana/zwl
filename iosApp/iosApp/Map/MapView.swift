import SwiftUI
import MapLibre
import CoreLocation
import shared

/// One downloaded offline area as a MapLibre-ready `mbtiles://` source input
/// (path resolved by the view model via the shared `OfflineAreaFiles`).
struct OfflineTileArea {
    let id: Int64
    let path: String
}

struct MapView: UIViewRepresentable {

    let zonesJson: String
    let bansJson: String
    let poisJson: String
    let showBans: Bool
    let showAccommodation: Bool
    let showRest: Bool
    let showShelters: Bool
    let showFireplaces: Bool
    let showViewpoints: Bool
    let showParking: Bool
    let showEducation: Bool
    let showOthers: Bool
    let isOffline: Bool
    let followsUser: Bool
    let userLatitude: Double?
    let userLongitude: Double?
    let recenterSignal: Int
    let savedPointsJson: String
    let pendingMarkerJson: String
    let centerSavedPointLatitude: Double?
    let centerSavedPointLongitude: Double?
    let centerSavedPointSignal: Int
    let offlineTileSources: [OfflineTileArea]
    let offlineSourcesSignal: Int
    let focusAreaRegion: MapRegion?
    let focusAreaSignal: Int
    let onTapZone: (String?) -> Void
    let onTapBan: (Int64) -> Void
    let onTapPoi: (String) -> Void
    let onTapSavedPoint: (Int64) -> Void
    let onTapBackground: () -> Void
    let onVisibleRegionChange: (MapRegion) -> Void
    let onLongPressPoint: (Double, Double) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> MLNMapView {
        // Load our OSM raster style directly from JSON at init time to avoid
        // both a default-style flash and the iOS race where styleJSON set right
        // after init gets overwritten by the still-loading default style.
        let map = MLNMapView(frame: .zero, styleJSON: MapStyle.shared.OSM_STYLE_JSON)
        map.delegate = context.coordinator
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.minimumZoomLevel = MapStyle.shared.MIN_ZOOM
        map.maximumZoomLevel = MapStyle.shared.MAX_ZOOM
        // MapLibre iOS renders on the main thread; on this iPad class 60fps is
        // unachievable so the renderer alternates smooth bursts with long frame
        // stalls. Cap at 30 for a uniform rhythm, and stop background tile
        // prefetch from competing with the visible raster.
        map.preferredFramesPerSecond = .lowPower
        map.prefetchesTiles = true
        map.showsUserLocation = true
        map.allowsRotating = false
        map.userTrackingMode = .follow
        // Android parity: the user position is a direction arrow that rotates
        // with the device heading, not a plain dot.
        map.showsUserHeadingIndicator = true
        context.coordinator.mapView = map
        map.setCenter(
            CLLocationCoordinate2D(latitude: MapStyle.shared.DEFAULT_LAT,
                                   longitude: MapStyle.shared.DEFAULT_LNG),
            // Start lighter than DEFAULT_ZOOM: the initial tile burst is the
            // biggest single startup block on this iPad class. The first GPS
            // fix later re-centers to DEFAULT_ZOOM (see handleCentering).
            // z10 vs z12: ~16x fewer base tiles at the first fill.
            zoomLevel: 10,
            animated: false
        )

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        map.addGestureRecognizer(tap)

        // Long-press to set the pending saved-point card (Android
        // `onMapLongClickListener` parity). MapLibre short-taps and long-presses
        // fire independently, so the existing tap handling is untouched.
        let longPress = UILongPressGestureRecognizer(target: context.coordinator,
                                                     action: #selector(Coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = 0.5
        map.addGestureRecognizer(longPress)

        context.coordinator.scheduleStyleProbe()
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.zonesJson = zonesJson
        coordinator.bansJson = bansJson
        coordinator.poisJson = poisJson
        coordinator.markJsonDirty()
        coordinator.showBans = showBans
        coordinator.showAccommodation = showAccommodation
        coordinator.showRest = showRest
        coordinator.showShelters = showShelters
        coordinator.showFireplaces = showFireplaces
        coordinator.showViewpoints = showViewpoints
        coordinator.showParking = showParking
        coordinator.showEducation = showEducation
        coordinator.showOthers = showOthers
        coordinator.isOffline = isOffline
        coordinator.followsUser = followsUser
        coordinator.onTapZone = onTapZone
        coordinator.onTapBan = onTapBan
        coordinator.onTapPoi = onTapPoi
        coordinator.onTapSavedPoint = onTapSavedPoint
        coordinator.onTapBackground = onTapBackground
        coordinator.onVisibleRegionChange = onVisibleRegionChange
        coordinator.savedPointsJson = savedPointsJson
        coordinator.pendingMarkerJson = pendingMarkerJson
        coordinator.centerSavedPointLatitude = centerSavedPointLatitude
        coordinator.centerSavedPointLongitude = centerSavedPointLongitude
        coordinator.centerSavedPointSignal = centerSavedPointSignal
        // Sources must be current BEFORE isOffline's didSet re-applies them.
        coordinator.offlineTileSources = offlineTileSources
        coordinator.focusAreaRegion = focusAreaRegion
        coordinator.offlineSourcesSignal = offlineSourcesSignal
        coordinator.focusAreaSignal = focusAreaSignal
        coordinator.onLongPressPoint = onLongPressPoint
        coordinator.applySourcesIfReady()
        coordinator.handleCentering(userLatitude: userLatitude,
                                    userLongitude: userLongitude,
                                    recenterSignal: recenterSignal)
        coordinator.applySavedPointLayersIfNeeded()
        coordinator.handleSavedPointCentering()
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var mapView: MLNMapView?
        var zonesJson = ""
        var bansJson = ""
        var poisJson = ""
        var showBans = true
        var showAccommodation = true
        var showRest = true
        var showShelters = true
        var showFireplaces = true
        var showViewpoints = true
        var showParking = true
        var showEducation = true
        var showOthers = true
        /// Whether the device currently has no network path (`isOffline` from
        /// the main view model, NWPathMonitor). When true we add one local
        /// `mbtiles://` source per downloaded area so the map keeps rendering
        /// without network (Android `RasterSource(url: mbtiles://...)`
        /// parity). Idempotent; re-runs when the flag flips.
        var isOffline = false {
            didSet {
                guard oldValue != isOffline else { return }
                applyOfflineTileSource()
            }
        }
        /// Downloaded areas as `mbtiles://` source inputs; re-applied when the
        /// view model bumps `offlineSourcesSignal` (download/delete/refresh).
        var offlineTileSources: [OfflineTileArea] = []
        var offlineSourcesSignal = 0 {
            didSet {
                guard oldValue != offlineSourcesSignal else { return }
                applyOfflineTileSource()
            }
        }
        /// Tap on a managed area -> fly the camera to its bounding box.
        var focusAreaRegion: MapRegion?
        var focusAreaSignal = 0 {
            didSet {
                guard oldValue != focusAreaSignal else { return }
                guard let region = focusAreaRegion else { return }
                flyToArea(region)
            }
        }
        var followsUser = true {
            didSet {
                guard oldValue != followsUser else { return }
                mapView?.userTrackingMode = followsUser ? .follow : .none
            }
        }
        var onTapZone: ((String?) -> Void) = { _ in }
        var onTapBan: (Int64) -> Void = { _ in }
        var onTapPoi: (String) -> Void = { _ in }
        var onTapSavedPoint: (Int64) -> Void = { _ in }
        var onTapBackground: () -> Void = {}
        var onVisibleRegionChange: (MapRegion) -> Void = { _ in }
        var savedPointsJson = ""
        var pendingMarkerJson = ""
        var centerSavedPointLatitude: Double?
        var centerSavedPointLongitude: Double?
        var centerSavedPointSignal = 0
        var onLongPressPoint: (Double, Double) -> Void = { _, _ in }

        private var lastSavedPointCenterSignal = 0
        private var lastSavedPointLayerSignature = ""

        private var styleLoaded = false
        private var styleFallbackTried = false
        private var styleProbeTicks = 0
        private var styleProbeTimer: Timer?
        private var layersReady = false
        private var hasCenteredOnStartup = false
        private var lastRecenterSignal = 0

        // On-disk GeoJSON files feeding the overlay's URL-backed shape sources.
        // Written once per data change on a background QoS; `updateUIView`
        // re-runs are free.
        private var dataFiles: GeoJsonFileWriter.Files?
        private var writeTask: Task<Void, Never>?
        private var lastJsonSignature = ""
        private var lastToggleSignature = ""
        private var lastAppliedSignature: UInt64 = 0
        /// Cheap cached UTF-8 byte counts of the big GeoJSON blobs, refreshed
        /// only when a json* stored below changes. Keeps the steady-state
        /// applySourcesIfReady() gate O(1) instead of re-scanning hundreds of
        /// KB per grapheme (String.count) at GPS/data-tick frequency.
        private var zonesByteCount = 0
        private var bansByteCount = 0
        private var poisByteCount = 0
        private var zonesDirty = true
        private var bansDirty = true
        private var poisDirty = true

        // Saved-point markers (Android own-points magenta parity) — always-on
        // circle layers fed from the shared `savedPointsToGeoJson`.
        private let ownPointsSourceId = "own-points-source"
        private let ownPointsLayerId = "own-points-layer"
        private let pendingMarkerSourceId = "pending-marker-source"
        private let pendingMarkerLayerId = "pending-marker-layer"
        private static let ownPointColor = UIColor(red: 233.0 / 255.0, green: 30.0 / 255.0, blue: 99.0 / 255.0, alpha: 1.0)

        // Vector overlay ids. Sources are URL-backed so
        // MapLibre tiles them on its worker threads.
        private var vectorInstalled = false
        private let zoneFillId = "zone-fill-layer"
        private let zoneLineId = "zone-line-layer"
        private let banFillId = "ban-fill-layer"
        private let banLineId = "ban-line-layer"
        private let poiShelterId = "poi-shelter-layer"
        private let poiFireplaceId = "poi-fireplace-layer"
        private let poiOtherId = "poi-other-layer"
        private let poiAccommodationId = "poi-accommodation-layer"
        private let poiRestId = "poi-rest-layer"
        private let poiViewpointId = "poi-viewpoint-layer"
        private let poiParkingId = "poi-parking-layer"
        private let poiEducationId = "poi-education-layer"
        private let vectorZoneSourceId = "vec-zone-source"
        private let vectorBanSourceId = "vec-ban-source"
        private let vectorShelterSourceId = "vec-poi-shelter-source"
        private let vectorFireplaceSourceId = "vec-poi-fireplace-source"
        private let vectorOtherSourceId = "vec-poi-other-source"
        private let vectorAccommodationSourceId = "vec-poi-accommodation-source"
        private let vectorRestSourceId = "vec-poi-rest-source"
        private let vectorViewpointSourceId = "vec-poi-viewpoint-source"
        private let vectorParkingSourceId = "vec-poi-parking-source"
        private let vectorEducationSourceId = "vec-poi-education-source"

        private var parent: MapView

        init(_ parent: MapView) {
            self.parent = parent
        }

        // MARK: MLNMapViewDelegate

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            handleStyleLoaded(mapView)
        }

        func mapView(_ mapView: MLNMapView, didFailLoadingStyle styleIdentifier: String?, withError error: Error) {
            recordStyleFailure(error)
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            recordStyleFailure(error)
        }

        func mapView(_ mapView: MLNMapView, didUpdate userLocation: MLNUserLocation?) {
            // We rely on the built-in user location dot; status computation is
            // driven by CLLocationManager in the view model.
        }

        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            // A gesture (or programmatic camera move) has settled.
            let bounds = mapView.visibleCoordinateBounds
            onVisibleRegionChange(
                MapRegion(latSouth: bounds.sw.latitude,
                          latNorth: bounds.ne.latitude,
                          lonWest: bounds.sw.longitude,
                          lonEast: bounds.ne.longitude)
            )
        }

        // MARK: Style readiness

        /// Called from the delegate hooks AND from the probe timer, so the
        /// overlay layers never depend on a single delegate callback. Both
        /// `mapView(_:didFinishLoading:)` and the poller (map.style != nil)
        /// converge here.
        private func handleStyleLoaded(_ mapView: MLNMapView) {
            guard !styleLoaded else { return }
            styleLoaded = true
            applySourcesIfReady()
            applySavedPointLayersIfNeeded()
            applyOfflineTileSource()
            stopStyleProbe()
        }

        private func recordStyleFailure(_ error: Error) {
            if !styleFallbackTried {
                attemptStyleReloadFromFile()
            }
        }

        /// MapLibre iOS can leave the in-memory style in a broken state after a
        /// bad `styleJSON` parse; retry by loading the same JSON from a local
        /// file (the `styleURL` path is far more battle-tested).
        private func attemptStyleReloadFromFile() {
            guard !styleFallbackTried, let mapView = mapView else { return }
            styleFallbackTried = true
            let json = MapStyle.shared.OSM_STYLE_JSON
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("osm-style.json")
            try? json.write(to: url, atomically: true, encoding: .utf8)
            mapView.styleURL = url
        }

        /// Polls for the style loading. Fragile parts of this map code path only
        /// run after the style exists; never fail silently again.
        fileprivate func scheduleStyleProbe() {
            styleProbeTimer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: true) { [weak self] _ in
                guard let self = self else { return }
                if self.styleLoaded {
                    self.stopStyleProbe()
                    return
                }
                self.styleProbeTicks += 1
                if let map = self.mapView, map.style != nil {
                    self.handleStyleLoaded(map)
                    return
                }
                // ~12s with no style at all: try the file-URL retry once.
                if self.styleProbeTicks >= 30, !self.styleFallbackTried {
                    self.attemptStyleReloadFromFile()
                }
                if self.styleProbeTicks >= 60 {
                    self.stopStyleProbe()
                }
            }
        }

        private func stopStyleProbe() {
            styleProbeTimer?.invalidate()
            styleProbeTimer = nil
        }

        // MARK: Layers

        private func removeLayerIfPresent(_ id: String, style: MLNStyle) {
            if let layer = style.layer(withIdentifier: id) {
                style.removeLayer(layer)
            }
        }

        private func removeSourceIfPresent(_ id: String, style: MLNStyle) {
            if let source = style.source(withIdentifier: id) {
                style.removeSource(source)
            }
        }

        // MARK: Vector overlay (A/B via the "Wektor (diagnoza)" toggle)

        /// Restores the pre-raster vector overlay: URL-backed `MLNShapeSource`s
        /// so MapLibre cuts tiles on its worker threads (no main-thread
        /// tessellation), zones/bans as fill+line, POIs as per-category circles.
        /// Only runs once per data change / mode switch, after the style loads.
        private func ensureVectorSourcesAndLayers() {
            guard styleLoaded, mapView != nil else { return }
            guard let style = mapView?.style, let files = dataFiles, !vectorInstalled else { return }

            removeVectorOverlay(style)

            let shapeOptions: [MLNShapeSourceOption: Any]? = [.simplificationTolerance: 1.0]
            let zoneSource = MLNShapeSource(identifier: vectorZoneSourceId,
                                            url: files.zonesURL,
                                            options: shapeOptions)
            style.addSource(zoneSource)
            let zoneFill = MLNFillStyleLayer(identifier: zoneFillId, source: zoneSource)
            zoneFill.fillColor = NSExpression(forConstantValue: UIColor(red: 0.10, green: 0.65, blue: 0.25, alpha: 0.35))
            zoneFill.fillOutlineColor = NSExpression(forConstantValue: UIColor(red: 0.0, green: 0.4, blue: 0.1, alpha: 1.0))
            zoneFill.fillAntialiased = NSExpression(forConstantValue: false)
            style.addLayer(zoneFill)
            let zoneLine = MLNLineStyleLayer(identifier: zoneLineId, source: zoneSource)
            zoneLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.0, green: 0.45, blue: 0.1, alpha: 0.9))
            zoneLine.lineWidth = NSExpression(forConstantValue: 2.0)
            style.addLayer(zoneLine)

            let banSource = MLNShapeSource(identifier: vectorBanSourceId,
                                           url: files.bansURL,
                                           options: shapeOptions)
            style.addSource(banSource)
            let banFill = MLNFillStyleLayer(identifier: banFillId, source: banSource)
            banFill.fillColor = NSExpression(forConstantValue: banFillColor(showBans))
            banFill.fillOutlineColor = NSExpression(forConstantValue: banOutlineColor(showBans))
            banFill.fillAntialiased = NSExpression(forConstantValue: false)
            style.addLayer(banFill)
            let banLine = MLNLineStyleLayer(identifier: banLineId, source: banSource)
            banLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.7, green: 0.05, blue: 0.05, alpha: 0.9))
            banLine.lineWidth = NSExpression(forConstantValue: 2.0)
            banLine.lineOpacity = NSExpression(forConstantValue: showBans ? 0.9 : 0.0)
            style.addLayer(banLine)

            // One URL source per POI category; toggling switches paint opacity
            // only (layers stay layout-visible, so baked tiles stay hot).
            let shelterSource = MLNShapeSource(identifier: vectorShelterSourceId,
                                               url: files.shelterURL,
                                               options: nil)
            style.addSource(shelterSource)
            style.addLayer(poiCircleLayer(identifier: poiShelterId,
                                          source: shelterSource,
                                          color: UIColor(red: 0.10, green: 0.65, blue: 0.25, alpha: 1.0),
                                          isOpaque: showShelters))
            let fireplaceSource = MLNShapeSource(identifier: vectorFireplaceSourceId,
                                                 url: files.fireplaceURL,
                                                 options: nil)
            style.addSource(fireplaceSource)
            style.addLayer(poiCircleLayer(identifier: poiFireplaceId,
                                          source: fireplaceSource,
                                          color: UIColor.systemOrange,
                                          isOpaque: showFireplaces))
            let otherSource = MLNShapeSource(identifier: vectorOtherSourceId,
                                             url: files.otherURL,
                                             options: nil)
            style.addSource(otherSource)
            style.addLayer(poiCircleLayer(identifier: poiOtherId,
                                          source: otherSource,
                                          color: UIColor.systemBlue,
                                          isOpaque: showOthers))
            let accommodationSource = MLNShapeSource(identifier: vectorAccommodationSourceId,
                                                     url: files.accommodationURL,
                                                     options: nil)
            style.addSource(accommodationSource)
            style.addLayer(poiCircleLayer(identifier: poiAccommodationId,
                                          source: accommodationSource,
                                          color: UIColor(red: 0.11, green: 0.37, blue: 0.13, alpha: 1.0),
                                          isOpaque: showAccommodation))
            let restSource = MLNShapeSource(identifier: vectorRestSourceId,
                                            url: files.restURL,
                                            options: nil)
            style.addSource(restSource)
            style.addLayer(poiCircleLayer(identifier: poiRestId,
                                          source: restSource,
                                          color: UIColor(red: 0.33, green: 0.55, blue: 0.18, alpha: 1.0),
                                          isOpaque: showRest))
            let viewpointSource = MLNShapeSource(identifier: vectorViewpointSourceId,
                                                 url: files.viewpointURL,
                                                 options: nil)
            style.addSource(viewpointSource)
            style.addLayer(poiCircleLayer(identifier: poiViewpointId,
                                          source: viewpointSource,
                                          color: UIColor(red: 0.0, green: 0.59, blue: 0.65, alpha: 1.0),
                                          isOpaque: showViewpoints))
            let parkingSource = MLNShapeSource(identifier: vectorParkingSourceId,
                                               url: files.parkingURL,
                                               options: nil)
            style.addSource(parkingSource)
            style.addLayer(poiCircleLayer(identifier: poiParkingId,
                                          source: parkingSource,
                                          color: UIColor(red: 0.36, green: 0.25, blue: 0.22, alpha: 1.0),
                                          isOpaque: showParking))
            let educationSource = MLNShapeSource(identifier: vectorEducationSourceId,
                                                 url: files.educationURL,
                                                 options: nil)
            style.addSource(educationSource)
            style.addLayer(poiCircleLayer(identifier: poiEducationId,
                                          source: educationSource,
                                          color: UIColor(red: 0.48, green: 0.12, blue: 0.64, alpha: 1.0),
                                          isOpaque: showEducation))

            vectorInstalled = true
            layersReady = true
            // Keep the magenta markers above the overlay after a re-install.
            restackSavedPointLayers(style)
        }

        private func banFillColor(_ visible: Bool) -> UIColor {
            visible ? UIColor(red: 0.8, green: 0.1, blue: 0.1, alpha: 0.3) : UIColor.clear
        }

        private func banOutlineColor(_ visible: Bool) -> UIColor {
            visible ? UIColor(red: 0.6, green: 0.0, blue: 0.0, alpha: 1.0) : UIColor.clear
        }

        private func poiCircleLayer(identifier: String,
                                    source: MLNShapeSource,
                                    color: UIColor,
                                    isOpaque: Bool) -> MLNCircleStyleLayer {
            let layer = MLNCircleStyleLayer(identifier: identifier, source: source)
            // Zoom-scaled radius (Android parity): dots shrink hard at far zoom
            // (region scale) so they don't block the map, then grow with zoom.
            layer.circleRadius = NSExpression(mglJSONObject: [
                "step", ["zoom"], 3,
                7, 4,
                9, 5.5,
                11, 7,
                13, 11,
                14, 7
            ] as [Any])
            layer.circleColor = NSExpression(forConstantValue: color)
            layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            layer.circleStrokeWidth = NSExpression(forConstantValue: 1.5)
            layer.circleOpacity = NSExpression(forConstantValue: isOpaque ? 1.0 : 0.0)
            layer.circleStrokeOpacity = NSExpression(forConstantValue: isOpaque ? 1.0 : 0.0)
            return layer
        }

        private func removeVectorOverlay(_ style: MLNStyle) {
            for id in [zoneFillId, zoneLineId, banFillId, banLineId,
                       poiShelterId, poiFireplaceId, poiOtherId,
                       poiAccommodationId, poiRestId, poiViewpointId, poiParkingId, poiEducationId] {
                removeLayerIfPresent(id, style: style)
            }
            for id in [vectorZoneSourceId, vectorBanSourceId,
                        vectorShelterSourceId, vectorFireplaceSourceId, vectorOtherSourceId,
                        vectorAccommodationSourceId, vectorRestSourceId, vectorViewpointSourceId,
                        vectorParkingSourceId, vectorEducationSourceId] {
                removeSourceIfPresent(id, style: style)
            }
        }

        // MARK: Offline tile sources (mbtiles://)

        private static let maxOfflineSources = 64

        /// Re-applies the offline raster sources: every downloaded area gets
        /// its own `mbtiles://` source + layer below the OSM base layer (same
        /// style, so overlapping areas are harmless — Android parity). Sources
        /// are generation-stamped by index so stale ones can always be found
        /// and removed. The driver is closed by the packer once a download
        /// finishes, so the WAL is checkpointed and the files are safe for
        /// MapLibre to read directly.
        private func applyOfflineTileSource() {
            guard let style = mapView?.style else { return }

            for index in 0..<Self.maxOfflineSources {
                if let layer = style.layer(withIdentifier: "osm-offline-layer-\(index)") {
                    style.removeLayer(layer)
                }
                if let source = style.source(withIdentifier: "osm-offline-\(index)") {
                    style.removeSource(source)
                }
            }
            guard isOffline else { return }

            for (index, area) in offlineTileSources.enumerated() where index < Self.maxOfflineSources {
                guard FileManager.default.fileExists(atPath: area.path) else { continue }
                let source = MLNRasterTileSource(
                    identifier: "osm-offline-\(index)",
                    tileURLTemplates: ["mbtiles://file://\(area.path)"],
                    options: [.tileSize: 256]
                )
                style.addSource(source)
                let layer = MLNRasterStyleLayer(identifier: "osm-offline-layer-\(index)", source: source)
                layer.rasterOpacity = NSExpression(forConstantValue: 1.0)
                if let osm = style.layer(withIdentifier: "osm") {
                    style.insertLayer(layer, below: osm)
                } else {
                    style.addLayer(layer)
                }
            }
        }

        /// Animates the camera so the area's bounding box fits the viewport.
        private func flyToArea(_ region: MapRegion) {
            guard let map = mapView else { return }
            let bounds = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: region.latSouth, longitude: region.lonWest),
                ne: CLLocationCoordinate2D(latitude: region.latNorth, longitude: region.lonEast)
            )
            map.setVisibleCoordinateBounds(bounds, animated: true)
        }

        // MARK: Data application

        /// Marks the big JSON blobs as needing a re-hash. Called from
        /// updateUIView whenever any coordinate's json changes; keep the
        /// contentSignatureHash byte-count cache in sync with the inputs.
        fileprivate func markJsonDirty() {
            zonesDirty = true
            bansDirty = true
            poisDirty = true
        }

        func applySourcesIfReady() {
            guard styleLoaded, mapView != nil else { return }
            // Cheap gate. SwiftUI re-runs updateUIView (hence this) far more
            // often than any input actually changes — fresh closures handed to
            // the representable defeat its structural diff, so ANY @Published
            // write on MainView (GPS 1Hz, status, diagnostics) re-invokes it.
            // Never rebuild styles/strings unless the content signature moved;
            // this was the main-thread CPU burn behind the 202 CPU watchdog.
            // The hash is a single O(n) pass over UTF-8 bytes (no grapheme
            // String.count, no string pooling), so repeated calls are cheap.
            let hash = contentSignatureHash()
            guard hash != lastAppliedSignature else { return }
            lastAppliedSignature = hash
            scheduleWriteIfNeeded()
            ensureVectorSourcesAndLayers()
            refreshLayerVisibility()
        }

        /// Compact, deterministic key for the data + config that feeds the
        /// overlay pipeline. Computed via a single O(n) pass over the UTF-8
        /// bytes — no grapheme String.count and no ~20 interpolated strings
        /// per call (the previous version cost two full JSON rescans at GPS
        /// /data-tick frequency and was the main-thread burn in the 202 CPU
        /// watchdog). Cached: the big blobs only re-hash when their byte
        /// counts move; the toggles fold in on every call for free.
        private func contentSignatureHash() -> UInt64 {
            func byteCount(_ dirty: inout Bool, _ cached: inout Int, _ value: String) -> Int {
                if dirty {
                    dirty = false
                    cached = value.utf8.count
                }
                return cached
            }
            let zonesCount = byteCount(&zonesDirty, &zonesByteCount, zonesJson)
            let bansCount = byteCount(&bansDirty, &bansByteCount, bansJson)
            let poisCount = byteCount(&poisDirty, &poisByteCount, poisJson)
            let sig = "\(zonesCount)|\(bansCount)|\(poisCount)|"
                + "\(showBans ? "1" : "0")\(showAccommodation ? "1" : "0")\(showRest ? "1" : "0")\(showShelters ? "1" : "0")\(showFireplaces ? "1" : "0")\(showViewpoints ? "1" : "0")\(showParking ? "1" : "0")\(showEducation ? "1" : "0")\(showOthers ? "1" : "0")"
            return StableHash.hash(sig)
        }

        /// Writes the GeoJSON to temp files (the overlay's URL source input) on
        /// a background QoS once per data change.
        private func scheduleWriteIfNeeded() {
            zonesDirty = true
            bansDirty = true
            poisDirty = true
            let jsonSignature = "\(zonesJson.count)|\(bansJson.count)|\(poisJson.count)"
            guard jsonSignature != lastJsonSignature else { return }
            lastJsonSignature = jsonSignature

            let zones = zonesJson
            let bans = bansJson
            let pois = poisJson
            writeTask?.cancel()
            writeTask = Task.detached(priority: .utility) {
                let files = GeoJsonFileWriter.makeFiles(zones: zones, bans: bans, pois: pois)
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.writeTask = nil
                    if let files = files {
                        self.applyFiles(files)
                    }
                }
            }
        }

        private func applyFiles(_ files: GeoJsonFileWriter.Files) {
            dataFiles = files

            // New dataset: invalidate the overlay — the on-disk files changed
            // underneath it.
            vectorInstalled = false
            layersReady = false
            ensureVectorSourcesAndLayers()
            refreshLayerVisibility()
        }

        /// Toggles switch paint properties only — the cheapest mutation (vector
        /// fill/line/circle opacity). No geometry, no data rebuild, no filter
        /// pass.
        private func refreshLayerVisibility() {
            guard layersReady, let style = mapView?.style else { return }
            let signature = "\(showBans)|\(showAccommodation)|\(showRest)|\(showShelters)|\(showFireplaces)|\(showViewpoints)|\(showParking)|\(showEducation)|\(showOthers)"
            guard signature != lastToggleSignature else { return }
            lastToggleSignature = signature
            refreshVectorVisibility(style)
        }

        private func refreshVectorVisibility(_ style: MLNStyle) {
            if let banFill = style.layer(withIdentifier: banFillId) as? MLNFillStyleLayer {
                banFill.fillColor = NSExpression(forConstantValue: banFillColor(showBans))
                banFill.fillOutlineColor = NSExpression(forConstantValue: banOutlineColor(showBans))
            }
            if let banLine = style.layer(withIdentifier: banLineId) as? MLNLineStyleLayer {
                banLine.lineOpacity = NSExpression(forConstantValue: showBans ? 0.9 : 0.0)
            }
            for entry in [(poiShelterId, showShelters),
                          (poiFireplaceId, showFireplaces),
                          (poiOtherId, showOthers),
                          (poiAccommodationId, showAccommodation),
                          (poiViewpointId, showViewpoints),
                          (poiParkingId, showParking),
                          (poiEducationId, showEducation)] {
                if let layer = style.layer(withIdentifier: entry.0) as? MLNCircleStyleLayer {
                    let opacity: Double = entry.1 ? 1.0 : 0.0
                    layer.circleOpacity = NSExpression(forConstantValue: opacity)
                    layer.circleStrokeOpacity = NSExpression(forConstantValue: opacity)
                }
            }
        }

        // MARK: Camera centering

        /// Centers the camera on the user position, once after the first fix and
        /// again on every explicit recenter request (Android parity).
        func handleCentering(userLatitude: Double?, userLongitude: Double?, recenterSignal: Int) {
            guard mapView != nil else { return }
            if userLatitude != nil, userLongitude != nil, !hasCenteredOnStartup {
                hasCenteredOnStartup = true
                centerOnUser(latitude: userLatitude, longitude: userLongitude, animated: false)
                return
            }
            if recenterSignal != lastRecenterSignal {
                lastRecenterSignal = recenterSignal
                centerOnUser(latitude: userLatitude, longitude: userLongitude, animated: true)
            }
        }

        private func centerOnUser(latitude: Double?, longitude: Double?, animated: Bool) {
            guard let lat = latitude, let lon = longitude, let mapView = mapView else { return }
            mapView.userTrackingMode = .none
            mapView.setCenter(
                CLLocationCoordinate2D(latitude: lat, longitude: lon),
                zoomLevel: MapStyle.shared.DEFAULT_ZOOM,
                animated: animated
            )
            mapView.userTrackingMode = followsUser ? .follow : .none
            let bounds = mapView.visibleCoordinateBounds
            onVisibleRegionChange(
                MapRegion(latSouth: bounds.sw.latitude,
                          latNorth: bounds.ne.latitude,
                          lonWest: bounds.sw.longitude,
                          lonEast: bounds.ne.longitude)
            )
        }

        // MARK: Saved-point layers + centering

        /// Installs/refreshes the own-points + pending-marker circle layers
        /// whenever their GeoJSON content changes. Cheap length-based gate so
        /// regular `updateUIView` re-runs stay free.
        func applySavedPointLayersIfNeeded() {
            guard styleLoaded, let style = mapView?.style else { return }
            let signature = "\(savedPointsJson.count)|\(pendingMarkerJson.count)"
            guard signature != lastSavedPointLayerSignature else { return }
            lastSavedPointLayerSignature = signature
            installSavedPointLayers(style)
        }

        /// Unconditionally restacks the saved-point layers on top — used after
        /// the raster/vector overlay pipeline (re)installs so the magenta
        /// markers never end up underneath it.
        private func restackSavedPointLayers(_ style: MLNStyle) {
            installSavedPointLayers(style)
        }

        private func installSavedPointLayers(_ style: MLNStyle) {
            removeLayerIfPresent(ownPointsLayerId, style: style)
            removeSourceIfPresent(ownPointsSourceId, style: style)
            removeLayerIfPresent(pendingMarkerLayerId, style: style)
            removeSourceIfPresent(pendingMarkerSourceId, style: style)

            if let shape = Self.shape(fromGeoJson: savedPointsJson) {
                let source = MLNShapeSource(identifier: ownPointsSourceId, shape: shape, options: nil)
                style.addSource(source)
                let layer = MLNCircleStyleLayer(identifier: ownPointsLayerId, source: source)
                layer.circleColor = NSExpression(forConstantValue: Self.ownPointColor)
                layer.circleRadius = NSExpression(forConstantValue: 7.0)
                layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
                layer.circleStrokeWidth = NSExpression(forConstantValue: 2.0)
                style.addLayer(layer)
            }

            if let markerShape = Self.shape(fromGeoJson: pendingMarkerJson) {
                let source = MLNShapeSource(identifier: pendingMarkerSourceId, shape: markerShape, options: nil)
                style.addSource(source)
                let layer = MLNCircleStyleLayer(identifier: pendingMarkerLayerId, source: source)
                layer.circleColor = NSExpression(forConstantValue: Self.ownPointColor)
                layer.circleRadius = NSExpression(forConstantValue: 4.0)
                layer.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
                layer.circleStrokeWidth = NSExpression(forConstantValue: 4.0)
                layer.circleOpacity = NSExpression(forConstantValue: 0.9)
                style.addLayer(layer)
            }
        }

        private static func shape(fromGeoJson json: String) -> MLNShape? {
            guard !json.isEmpty, let data = json.data(using: .utf8),
                  let shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue) else {
                return nil
            }
            return shape
        }

        /// Centers the camera on a saved point (zoom 15) when the list signals
        /// one via `centerSavedPointSignal` (Android `focusSavedPoint` parity).
        func handleSavedPointCentering() {
            guard centerSavedPointSignal != lastSavedPointCenterSignal,
                  let lat = centerSavedPointLatitude,
                  let lon = centerSavedPointLongitude,
                  let mapView = mapView else { return }
            lastSavedPointCenterSignal = centerSavedPointSignal
            mapView.userTrackingMode = .none
            mapView.setCenter(CLLocationCoordinate2D(latitude: lat, longitude: lon),
                              zoomLevel: 15.0,
                              animated: true)
            mapView.userTrackingMode = followsUser ? .follow : .none
        }

        // MARK: Tap handling

        /// Long-press on the map opens the pending saved-point card (Android
        /// `onMapLongClickListener` parity). Only the first `.began` phase fires.
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began, let mapView = mapView else { return }
            let point = gesture.location(in: mapView)
            let coord = mapView.convert(point, toCoordinateFrom: mapView)
            onLongPressPoint(coord.latitude, coord.longitude)
        }

        /// Overlay hit-testing goes through `visibleFeatures` (own saved points
        /// first, then POIs, zones, bans — Android parity order).
        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = mapView else {
                onTapBackground()
                return
            }
            let point = gesture.location(in: mapView)
            let tapRect = CGRect(x: point.x - 30, y: point.y - 30, width: 60, height: 60)

            // Own saved points are always a vector circle layer, so hit-test
            // them first (Android parity: a tap on a saved point opens its
            // properties).
            if !savedPointsJson.isEmpty {
                let own = mapView.visibleFeatures(in: tapRect,
                                                  styleLayerIdentifiers: [ownPointsLayerId])
                if let feature = own.first,
                   let id = (feature.attribute(forKey: "id") as? NSNumber)?.int64Value {
                    onTapSavedPoint(id)
                    return
                }
            }

            handleVectorTap(mapView, tapRect)
        }

        private func handleVectorTap(_ mapView: MLNMapView, _ tapRect: CGRect) {
            let pois = mapView.visibleFeatures(
                in: tapRect,
                styleLayerIdentifiers: [poiShelterId, poiFireplaceId, poiOtherId,
                                        poiAccommodationId, poiViewpointId, poiParkingId, poiEducationId]
            )
            if let poiFeature = pois.first {
                if let name = poiFeature.attribute(forKey: "name") as? String {
                    onTapPoi(name)
                    return
                }
            }

            let zones = mapView.visibleFeatures(in: tapRect, styleLayerIdentifiers: [zoneFillId])
            if let zoneFeature = zones.first {
                let name = zoneFeature.attribute(forKey: "name") as? String
                onTapZone(name)
                return
            }

            let bans = mapView.visibleFeatures(in: tapRect, styleLayerIdentifiers: [banFillId])
            if let banFeature = bans.first {
                if let remoteIdNumber = banFeature.attribute(forKey: "remoteId") as? NSNumber {
                    onTapBan(remoteIdNumber.int64Value)
                }
                return
            }

            onTapBackground()
        }
    }
}

/// Stable FNV-1a out of the exact input string that feeds the overlay
/// pipeline. Kept trivial and dependency-free so the main-thread gate in
/// applySourcesIfReady() can run at GPS/data tick frequency without doing
/// myString.count grapheme scans or pooling ~20 interpolated strings.
fileprivate enum StableHash {
    static func hash(_ input: String) -> UInt64 {
        var h: UInt64 = 0xcbf29ce484222325
        for byte in input.utf8 {
            h ^= UInt64(byte)
            h = h &* 0x100000001b3
        }
        return h
    }
}