import SwiftUI
import MapLibre
import CoreLocation
import shared

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
    let overlayEnabled: Bool
    let vectorOverlay: Bool
    let baseEnabled: Bool
    let followsUser: Bool
    let showHeading: Bool
    let showUserDot: Bool
    let userLatitude: Double?
    let userLongitude: Double?
    let recenterSignal: Int
    let onTapZone: (String?) -> Void
    let onTapBan: (Int64) -> Void
    let onTapPoi: (String) -> Void
    let onTapBackground: () -> Void
    let onVisibleRegionChange: (MapRegion) -> Void
    let onDiagnostics: (String) -> Void

    func makeCoordinator() -> Coordinator {
        let coordinator = Coordinator(self)
        // Start the freeze meter before the map is even constructed so the
        // diagnostics capture the MapLibre init / styleJSON parse block too.
        coordinator.startStallProbe()
        return coordinator
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

        context.coordinator.scheduleStyleProbe()
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.zonesJson = zonesJson
        coordinator.bansJson = bansJson
        coordinator.poisJson = poisJson
        coordinator.showBans = showBans
        coordinator.showAccommodation = showAccommodation
        coordinator.showRest = showRest
        coordinator.showShelters = showShelters
        coordinator.showFireplaces = showFireplaces
        coordinator.showViewpoints = showViewpoints
        coordinator.showParking = showParking
        coordinator.showEducation = showEducation
        coordinator.showOthers = showOthers
        coordinator.overlayEnabled = overlayEnabled
        coordinator.vectorOverlay = vectorOverlay
        coordinator.baseEnabled = baseEnabled
        coordinator.followsUser = followsUser
        coordinator.showHeading = showHeading
        coordinator.showUserDot = showUserDot
        coordinator.onTapZone = onTapZone
        coordinator.onTapBan = onTapBan
        coordinator.onTapPoi = onTapPoi
        coordinator.onTapBackground = onTapBackground
        coordinator.onVisibleRegionChange = onVisibleRegionChange
        coordinator.onDiagnostics = onDiagnostics
        coordinator.applySourcesIfReady()
        coordinator.handleCentering(userLatitude: userLatitude,
                                    userLongitude: userLongitude,
                                    recenterSignal: recenterSignal)
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
        var overlayEnabled = true {
            didSet {
                guard oldValue != overlayEnabled else { return }
                resetStallMeter()
                applyOverlayEnabled()
            }
        }
        /// A/B: switch the overlay between the current raster pipeline and the
        /// pre-raster vector pipeline (URL-backed `MLNShapeSource`s) at runtime,
        /// reusing the same on-disk GeoJSON files. Defaults to raster.
        var vectorOverlay = false {
            didSet {
                guard oldValue != vectorOverlay else { return }
                resetStallMeter()
                applyOverlayMode()
            }
        }
        /// Diagnostics: fades the OSM base raster to 0/1 to measure what the
        /// base layer alone costs on the main thread (and to give a usable
        /// "fast map" while isolating it).
        var baseEnabled = true {
            didSet {
                guard oldValue != baseEnabled else { return }
                resetStallMeter()
                applyBaseEnabled()
            }
        }
        var followsUser = true {
            didSet {
                guard oldValue != followsUser else { return }
                resetStallMeter()
                mapView?.userTrackingMode = followsUser ? .follow : .none
            }
        }
        var showHeading = true {
            didSet {
                guard oldValue != showHeading else { return }
                resetStallMeter()
                mapView?.showsUserHeadingIndicator = showHeading
            }
        }
        /// Diagnostics: the native location dot re-renders the map on every GPS
        /// tick (~1Hz) from the engine's own listener. Toggling it off tests
        /// whether that alone drives the recurring stalls.
        var showUserDot = true {
            didSet {
                guard oldValue != showUserDot else { return }
                resetStallMeter()
                mapView?.showsUserLocation = showUserDot
            }
        }
        var onTapZone: ((String?) -> Void) = { _ in }
        var onTapBan: (Int64) -> Void = { _ in }
        var onTapPoi: (String) -> Void = { _ in }
        var onTapBackground: () -> Void = {}
        var onVisibleRegionChange: (MapRegion) -> Void = { _ in }
        var onDiagnostics: (String) -> Void = { _ in }

        private var styleLoaded = false
        private var styleFinishCount = 0
        private var styleErrorText = ""
        private var styleFallbackTried = false
        private var styleProbeTicks = 0
        private var styleProbeTimer: Timer?
        private var stallProbeTimer: Timer?
        private var lastStallProbe = Date()
        private var maxStall: TimeInterval = 0
        /// Startup phase timestamps (seconds since the coordinator was created),
        /// to localize the startup mega-stall: style loaded / data files ready /
        /// overlay sources installed.
        private let startTimestamp = Date()
        private var tStyle: TimeInterval?
        private var tData: TimeInterval?
        private var tOverlay: TimeInterval?
        private var layersReady = false
        private var layersApplied = false
        private var zoneFeatureCount = 0
        private var banFeatureCount = 0
        private var poiFeatureCount = 0
        private var poiShelterCount = 0
        private var poiFireplaceCount = 0
        private var poiOtherCount = 0
        private var poiAccommodationCount = 0
        private var poiRestCount = 0
        private var poiViewpointCount = 0
        private var poiParkingCount = 0
        private var poiEducationCount = 0
        private var jsonByteCount = (zones: 0, bans: 0, pois: 0)
        private var hasCenteredOnStartup = false
        private var lastRecenterSignal = 0
        private var lastDiagnosticsText = ""

        // On-disk GeoJSON files feeding the rasterizer. Written once per data
        // change on a background QoS; `updateUIView` re-runs are free.
        private var dataFiles: GeoJsonFileWriter.Files?
        private var lastJsonSignature = ""
        private var lastToggleSignature = ""
        private var lastAppliedSignature = ""

        // Raster overlay state. The rasterizer renders ONLY the current integer
        // zoom for the visible viewport (hundreds of tiles), off-main; neighbor
        // zooms are top-ups, so builds finish in ~seconds instead of never.
        private var catalog: OverlayRasterizer.Catalog?
        private var rasterTask: Task<Void, Never>?
        private var writeTask: Task<Void, Never>?
        private var pendingBuild: (region: OverlayRasterizer.Region, zoom: Int)?
        private var builtZoom = -1
        private var skippedBuildCount = 0
        private var lastSourceBounceAt = Date.distantPast
        private var lastInstalledZoom = -1
        private var isCameraMoving = false
        private var lastGoodRegion: OverlayRasterizer.Region?
        private static let sourceBounceMinInterval: TimeInterval = 2.0

        private let zoneRasterId = "zone-raster-layer"
        private let banRasterId = "ban-raster-layer"
        private let shelterRasterId = "poi-shelter-raster-layer"
        private let fireplaceRasterId = "poi-fireplace-raster-layer"
        private let otherRasterId = "poi-other-raster-layer"

        // Vector overlay ids (pre-raster pipeline). Sources are URL-backed so
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
            isCameraMoving = false
            let bounds = mapView.visibleCoordinateBounds
            onVisibleRegionChange(
                MapRegion(latSouth: bounds.sw.latitude,
                          latNorth: bounds.ne.latitude,
                          lonWest: bounds.sw.longitude,
                          lonEast: bounds.ne.longitude)
            )
            // Bake (or top up) the overlay tiles the current viewport needs.
            // The vector pipeline has nothing to bake — MapLibre tiles the
            // shape sources itself.
            if !vectorOverlay {
                scheduleRasterBuildFromCamera()
            }
        }

        func mapViewRegionIsChanging(_ mapView: MLNMapView) {
            isCameraMoving = true
        }

        // MARK: Style readiness

        /// Called from the delegate hooks AND from the probe timer, so the
        /// overlay layers never depend on a single delegate callback. Both
        /// `mapView(_:didFinishLoading:)` and the poller (map.style != nil)
        /// converge here.
        private func handleStyleLoaded(_ mapView: MLNMapView) {
            guard !styleLoaded else { return }
            styleLoaded = true
            styleFinishCount += 1
            tStyle = Date().timeIntervalSince(startTimestamp)
            installRasterIfCatalogReady()
            applySourcesIfReady()
            stopStyleProbe()
            publishDiagnostics()
        }

        private func recordStyleFailure(_ error: Error) {
            styleErrorText = error.localizedDescription
            publishDiagnostics()
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

        /// Freeze meter for QA: ticks every 0.5s on the main runloop and reports
        /// the longest gap (max main-thread stall since launch) in diagnostics.
        /// A button press that takes 2s shows up here exactly when the renderer
        /// hogged the main thread.
        fileprivate func startStallProbe() {
            // Release builds never display the freeze meter, so don't even run
            // the timer: per-tick publishDiagnostics used to build+emit the
            // diagnostics string on the main thread and (while the stall counter
            // kept growing) re-triggered SwiftUI update cycles -> CPU feedback
            // loop that tripped the 202 CPU watchdog. Flips on automatically
            // with DebugMapOverlay.isEnabled.
            guard DebugMapOverlay.isEnabled else { return }
            lastStallProbe = Date()
            stallProbeTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
                guard let self = self else { return }
                let now = Date()
                let gap = now.timeIntervalSince(self.lastStallProbe)
                self.lastStallProbe = now
                if gap > 0.75 {
                    self.maxStall = max(self.maxStall, gap)
                }
                self.publishDiagnostics()
            }
        }

        // MARK: Raster overlay lifecycle

        /// Single-producer background loop: one raster build at a time, newest
        /// pending region+zoom wins. Never touches the main thread while
        /// rendering; tiles are file-backed PNGs, so the renderer only decodes
        /// rasters.
        private func startRasterLoop() {
            guard rasterTask == nil else { return }
            guard !vectorOverlay else { return }
            guard overlayEnabled, let files = dataFiles else { return }
            guard let pending = pendingBuild else {
                installRasterIfCatalogReady()
                return
            }
            pendingBuild = nil
            let zoom = pending.zoom
            let zoomHigh = min(zoom + 1, 20)
            rasterTask = Task.detached(priority: .utility) {
                let result = OverlayRasterizer.build(files: files,
                                                     region: pending.region,
                                                     zMin: zoom,
                                                     zMax: zoomHigh)
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.rasterTask = nil
                    self.builtZoom = zoom
                    self.catalog = result.catalog
                    if result.catalog.tilesDone == 0 {
                        // Degenerate build (empty tile range — camera not fully
                        // laid out yet): never let this freeze force-rebuilds,
                        // so the next regionDidChange re-bakes with real bounds.
                        self.builtZoom = -1
                    }
                    if result.deltaWritten > 0, self.layersApplied {
                        // New files on disk. Bouncing sources makes MapLibre
                        // re-fetch them — but a full remove/add of five raster
                        // sources on the main thread is exactly the lift that
                        // causes periodic stalls. Zip the cost:
                        //  - zoom was rebuilt (catalog zoom range changed) ->
                        //    always bounce, or the installed source min/max
                        //    would freeze the overlay at the old zoom;
                        //  - same-zoom top-up -> never mid-gesture and at most
                        //    once per 2s, coalescing the pan's writes.
                        let zoomChanged = self.lastInstalledZoom != zoom
                        let now = Date()
                        let sinceLast = now.timeIntervalSince(self.lastSourceBounceAt)
                        if zoomChanged
                            || (!self.cameraGestureActive() && sinceLast >= Self.sourceBounceMinInterval) {
                            self.lastSourceBounceAt = now
                            self.layersApplied = false
                        }
                    }
                    self.installRasterIfCatalogReady()
                    self.publishDiagnostics()
                    self.startRasterLoop()
                }
            }
        }

        /// Figures out which integer zoom the camera is at and enqueues a build/
        /// top-up for the visible viewport. Cheap to call often (regionDidChange
        /// fires on every settle): the rasterizer skips tiles already on disk
        /// and force-rebuilds only when the camera crossed an integer zoom.
        private func scheduleRasterBuildFromCamera() {
            guard overlayEnabled, !vectorOverlay, let map = mapView, dataFiles != nil else { return }
            // MapLibre requests tiles at floor(zoomLevel): bake that zoom plus
            // one extra level so the first zoom-in is already crisp (no low-res
            // upscales while the bake catches up). Four levels up from the
            // viewport ≈ 5x the base tile count.
            let zoom = Int(map.zoomLevel.rounded(.down))
            let region = currentBuildRegion()
            let baseCount = OverlayRasterizer.tileCount(region: region, zoom: zoom)
            guard baseCount * 5 <= 6000 else {
                // Pathological view (e.g. window-sized region at z17+ before the
                // camera settles): do not spawn a multi-second build.
                skippedBuildCount += 1
                pendingBuild = nil
                return
            }
            let force = zoom != builtZoom
            if force || pendingBuild == nil {
                pendingBuild = (region, zoom)
            }
            startRasterLoop()
        }

        private func cameraGestureActive() -> Bool {
            isCameraMoving
        }

        private func currentBuildRegion() -> OverlayRasterizer.Region {
            var candidate: OverlayRasterizer.Region?
            if let map = mapView {
                let b = map.visibleCoordinateBounds
                let s = b.sw
                let n = b.ne
                let latPad = max(n.latitude - s.latitude, 0.02) * 0.05
                let lonPad = max(n.longitude - s.longitude, 0.02) * 0.05
                candidate = .init(latSouth: s.latitude - latPad,
                                  latNorth: n.latitude + latPad,
                                  lonWest: s.longitude - lonPad,
                                  lonEast: n.longitude + lonPad)
            } else {
                let lat = MapStyle.shared.DEFAULT_LAT
                candidate = .init(latSouth: lat - 0.15,
                                  latNorth: lat + 0.15,
                                  lonWest: MapStyle.shared.DEFAULT_LNG - 0.25,
                                  lonEast: MapStyle.shared.DEFAULT_LNG + 0.25)
            }
            if let candidate = candidate, candidate.isValid {
                lastGoodRegion = candidate
                return candidate
            }
            // Degenerate viewport (frame not laid out yet): reuse the last valid
            // region so the bake still covers the previous camera instead of
            // producing an empty tile range (tilesDone == 0).
            return lastGoodRegion ?? OverlayRasterizer.Region(
                latSouth: MapStyle.shared.DEFAULT_LAT - 0.15,
                latNorth: MapStyle.shared.DEFAULT_LAT + 0.15,
                lonWest: MapStyle.shared.DEFAULT_LNG - 0.25,
                lonEast: MapStyle.shared.DEFAULT_LNG + 0.25)
        }

        // MARK: Layers

        /// Installs the five raster sources + layers once, from the catalog.
        /// Only runs when the style AND a catalog exist and nothing is applied
        /// yet; bounces (`layersApplied = false`) force a re-install.
        private func installRasterIfCatalogReady() {
            guard overlayEnabled, !vectorOverlay else { return }
            guard let catalog = catalog, styleLoaded, mapView != nil else { return }
            guard let style = mapView?.style else { return }
            if !layersApplied {
                removeLayerIfPresent(zoneRasterId, style: style)
                removeLayerIfPresent(banRasterId, style: style)
                removeLayerIfPresent(shelterRasterId, style: style)
                removeLayerIfPresent(fireplaceRasterId, style: style)
                removeLayerIfPresent(otherRasterId, style: style)
                removeSourceIfPresent(OverlayRasterizer.zoneSourceId, style: style)
                removeSourceIfPresent(OverlayRasterizer.banSourceId, style: style)
                removeSourceIfPresent(OverlayRasterizer.shelterSourceId, style: style)
                removeSourceIfPresent(OverlayRasterizer.fireplaceSourceId, style: style)
                removeSourceIfPresent(OverlayRasterizer.otherSourceId, style: style)

                let specs: [(sourceId: String, template: String, layerId: String, visible: Bool)] = [
                    (OverlayRasterizer.zoneSourceId, OverlayRasterizer.tileTemplate(layer: .zones), zoneRasterId, true),
                    (OverlayRasterizer.banSourceId, OverlayRasterizer.tileTemplate(layer: .bans), banRasterId, showBans),
                    (OverlayRasterizer.shelterSourceId, OverlayRasterizer.tileTemplate(layer: .shelters), shelterRasterId, showShelters),
                    (OverlayRasterizer.fireplaceSourceId, OverlayRasterizer.tileTemplate(layer: .fireplaces), fireplaceRasterId, showFireplaces),
                    (OverlayRasterizer.otherSourceId, OverlayRasterizer.tileTemplate(layer: .others), otherRasterId, showOthers)
                ]
                for spec in specs {
                    let options: [MLNTileSourceOption: Any] = [
                        .tileSize: 512,
                        .minimumZoomLevel: catalog.zMin,
                        .maximumZoomLevel: catalog.zMax
                    ]
                    let source = MLNRasterTileSource(identifier: spec.sourceId,
                                                     tileURLTemplates: [spec.template],
                                                     options: options)
                    style.addSource(source)
                    let layer = MLNRasterStyleLayer(identifier: spec.layerId, source: source)
                    layer.rasterOpacity = NSExpression(forConstantValue: spec.visible ? 1.0 : 0.0)
                    style.addLayer(layer)
                }
                layersApplied = true
                layersReady = true
                lastInstalledZoom = catalog.zMin
                if tOverlay == nil {
                    tOverlay = Date().timeIntervalSince(startTimestamp)
                }
            }
            refreshLayerVisibility()
        }

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
            guard vectorOverlay, styleLoaded, mapView != nil else { return }
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
            if tOverlay == nil {
                tOverlay = Date().timeIntervalSince(startTimestamp)
            }
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

        private func removeRasterOverlay(_ style: MLNStyle) {
            for id in [zoneRasterId, banRasterId,
                       shelterRasterId, fireplaceRasterId, otherRasterId] {
                removeLayerIfPresent(id, style: style)
            }
            for id in [OverlayRasterizer.zoneSourceId,
                       OverlayRasterizer.banSourceId,
                       OverlayRasterizer.shelterSourceId,
                       OverlayRasterizer.fireplaceSourceId,
                       OverlayRasterizer.otherSourceId] {
                removeSourceIfPresent(id, style: style)
            }
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

        /// A/B switcher between the raster and vector overlay pipelines.
        private func applyOverlayMode() {
            guard let style = mapView?.style else { return }
            if vectorOverlay {
                // Raster -> vector: stop baking and drop the raster layers.
                rasterTask?.cancel()
                rasterTask = nil
                writeTask?.cancel()
                writeTask = nil
                pendingBuild = nil
                removeRasterOverlay(style)
                layersApplied = false
                vectorInstalled = false
                layersReady = false
                if overlayEnabled {
                    ensureVectorSourcesAndLayers()
                    refreshLayerVisibility()
                }
            } else {
                // Vector -> raster: drop the shape layers, rebuild the raster
                // overlay for the current viewport.
                removeVectorOverlay(style)
                vectorInstalled = false
                layersReady = false
                if overlayEnabled {
                    layersApplied = false
                    installRasterIfCatalogReady()
                    scheduleRasterBuildFromCamera()
                }
            }
            publishDiagnostics()
        }

        /// Diagnostics A/B: fully detaches (or re-attaches) the overlay while the
        /// app keeps running — whichever pipeline (raster/vector) is active.
        private func applyOverlayEnabled() {
            guard let style = mapView?.style else { return }
            if overlayEnabled {
                if vectorOverlay {
                    vectorInstalled = false
                    ensureVectorSourcesAndLayers()
                    refreshLayerVisibility()
                } else {
                    installRasterIfCatalogReady()
                    scheduleRasterBuildFromCamera()
                }
                publishDiagnostics()
            } else {
                rasterTask?.cancel()
                rasterTask = nil
                writeTask?.cancel()
                writeTask = nil
                pendingBuild = nil
                layersApplied = false
                vectorInstalled = false
                layersReady = false
                removeRasterOverlay(style)
                removeVectorOverlay(style)
                publishDiagnostics()
            }
        }

        /// Sets the OSM base raster opacity (diagnostics A/B). Idempotent —
        /// called from the toggle and re-applied whenever the style reloads.
        private func applyBaseEnabled() {
            guard let style = mapView?.style else { return }
            if let layer = style.layer(withIdentifier: "osm") as? MLNRasterStyleLayer {
                layer.rasterOpacity = NSExpression(forConstantValue: baseEnabled ? 1.0 : 0.0)
            }
        }

        // MARK: Data application

        func applySourcesIfReady() {
            guard styleLoaded, mapView != nil else { return }
            // Cheap gate. SwiftUI re-runs updateUIView (hence this) far more
            // often than any input actually changes — fresh closures handed to
            // the representable defeat its structural diff, so ANY @Published
            // write on MainView (GPS 1Hz, status, diagnostics) re-invokes it.
            // Never rebuild styles/strings unless the content signature moved;
            // this was the main-thread CPU burn behind the 202 CPU watchdog.
            let signature = contentSignature()
            guard signature != lastAppliedSignature else { return }
            lastAppliedSignature = signature
            scheduleWriteIfNeeded()
            if vectorOverlay {
                ensureVectorSourcesAndLayers()
                refreshLayerVisibility()
            } else {
                installRasterIfCatalogReady()
                refreshLayerVisibility()
            }
            applyBaseEnabled()
            publishDiagnostics()
        }

        /// Compact, deterministic key for the data + config that feeds the
        /// overlay pipeline. Cheap to compute (a few .count + bools) so it can
        /// gate a much more expensive style/string pipeline on the main thread.
        private func contentSignature() -> String {
            var sig = "v2|\(zonesJson.count)|\(bansJson.count)|\(poisJson.count)"
            sig += "|\(showBans ? "1" : "0")\(showAccommodation ? "1" : "0")\(showRest ? "1" : "0")\(showShelters ? "1" : "0")\(showFireplaces ? "1" : "0")\(showViewpoints ? "1" : "0")\(showParking ? "1" : "0")\(showEducation ? "1" : "0")\(showOthers ? "1" : "0")"
            sig += "|\(overlayEnabled ? "1" : "0")\(vectorOverlay ? "1" : "0")\(baseEnabled ? "1" : "0")"
            sig += "|\(followsUser ? "1" : "0")\(showHeading ? "1" : "0")\(showUserDot ? "1" : "0")"
            return sig
        }

        /// Writes the GeoJSON to temp files (the rasterizer's input) on a
        /// background QoS once per data change.
        private func scheduleWriteIfNeeded() {
            let jsonSignature = "\(zonesJson.count)|\(bansJson.count)|\(poisJson.count)"
            guard jsonSignature != lastJsonSignature else { return }
            lastJsonSignature = jsonSignature
            jsonByteCount = (zones: zonesJson.count,
                             bans: bansJson.count,
                             pois: poisJson.count)

            let zones = zonesJson
            let bans = bansJson
            let pois = poisJson
            // The write task must NOT live in `rasterTask`: it would keep the
            // build loop permanently blocked behind a stale "busy" reference
            // (the makeFiles task never cleared itself), leaving the overlay
            // stuck on "pending" forever.
            writeTask?.cancel()
            writeTask = Task.detached(priority: .utility) {
                let files = GeoJsonFileWriter.makeFiles(zones: zones, bans: bans, pois: pois)
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.writeTask = nil
                    if let files = files {
                        self.applyFiles(files)
                    } else {
                        self.publishDiagnostics()
                    }
                }
            }
        }

        private func applyFiles(_ files: GeoJsonFileWriter.Files) {
            dataFiles = files
            tData = Date().timeIntervalSince(startTimestamp)
            zoneFeatureCount = files.zoneCount
            banFeatureCount = files.banCount
            poiFeatureCount = files.shelterCount + files.fireplaceCount + files.otherCount
                + files.accommodationCount + files.restCount + files.viewpointCount + files.parkingCount + files.educationCount
            poiShelterCount = files.shelterCount
            poiFireplaceCount = files.fireplaceCount
            poiOtherCount = files.otherCount
            poiAccommodationCount = files.accommodationCount
            poiRestCount = files.restCount
            poiViewpointCount = files.viewpointCount
            poiParkingCount = files.parkingCount
            poiEducationCount = files.educationCount

            // New dataset: invalidate whichever overlay pipeline is active — the
            // on-disk files changed underneath it.
            if vectorOverlay {
                vectorInstalled = false
                layersReady = false
                ensureVectorSourcesAndLayers()
                refreshLayerVisibility()
            } else {
                rasterTask?.cancel()
                writeTask?.cancel()
                catalog = nil
                layersApplied = false
                layersReady = false
                builtZoom = -1
                pendingBuild = nil
                OverlayRasterizer.reset()
                scheduleRasterBuildFromCamera()
            }
            publishDiagnostics()
        }

        /// Toggles switch paint properties only — the cheapest mutation for either
        /// pipeline (raster opacity vs vector fill/line/circle opacity). No
        /// geometry, no data rebuild, no filter pass.
        private func refreshLayerVisibility() {
            guard layersReady, let style = mapView?.style else { return }
            let signature = "\(showBans)|\(showAccommodation)|\(showRest)|\(showShelters)|\(showFireplaces)|\(showViewpoints)|\(showParking)|\(showEducation)|\(showOthers)"
            guard signature != lastToggleSignature else { return }
            lastToggleSignature = signature

            if vectorOverlay {
                refreshVectorVisibility(style)
            } else {
                refreshRasterVisibility(style)
            }
        }

        private func refreshRasterVisibility(_ style: MLNStyle) {
            setRasterOpacity(banRasterId, visible: showBans, style: style)
            setRasterOpacity(shelterRasterId, visible: showShelters, style: style)
            setRasterOpacity(fireplaceRasterId, visible: showFireplaces, style: style)
            setRasterOpacity(otherRasterId, visible: showOthers, style: style)
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

        private func setRasterOpacity(_ id: String, visible: Bool, style: MLNStyle) {
            if let layer = style.layer(withIdentifier: id) as? MLNRasterStyleLayer {
                layer.rasterOpacity = NSExpression(forConstantValue: visible ? 1.0 : 0.0)
            }
        }

        // MARK: Diagnostics

        private func publishDiagnostics() {
            // Debug-only. When the overlay is disabled this return keeps the
            // big interpolated string (and the @Published `mapDiagnostics`
            // write it used to cause on every update) OFF the hot path.
            guard DebugMapOverlay.isEnabled else { return }
            let styleState = styleLoaded ? "YES(\(styleFinishCount))" : "NO"
            let fail = styleErrorText.isEmpty ? "-" : styleErrorText
            let layersState = layersReady ? "YES" : "NO"
            let tiles = catalog?.writtenTiles ?? 0
            let done = catalog?.tilesDone ?? 0
            let zMin = catalog?.zMin ?? 0
            let zMax = catalog?.zMax ?? 0
            // Localized "overlay" so QA can tell whether the raster pipeline ran.
            // `done` = tiles attempted, `tiles` = those actually written.
            // done==0 -> build had an empty/degenerate region; done>0==tiles ->
            // render failed. The "pending" tail exposes the stuck gate: file
            // presence, queued zoom, busy task, and budget-skip counter.
            let overlayText: String
            if !overlayEnabled {
                overlayText = "off"
            } else if vectorOverlay {
                overlayText = "vector layers=\(layersReady ? "YES" : "NO")"
            } else if layersReady {
                overlayText = "tiles=\(tiles)/\(done) z=\(zMin)-\(zMax) skip=\(skippedBuildCount)"
            } else {
                overlayText = "pending files=\(dataFiles != nil) q=\(pendingBuild?.zoom ?? -1) busy=\(rasterTask != nil) skip=\(skippedBuildCount)"
            }
            let text = """
            style=\(styleState) fail=\(fail) layers=\(layersState) dot=\(showUserDot ? "YES" : "NO") stall=\(String(format: "%.1f", maxStall))s
            phases s=\(fmt(tStyle)) d=\(fmt(tData)) o=\(fmt(tOverlay))
            overlay: \(overlayText)
            zones: json \(jsonByteCount.zones) feat \(zoneFeatureCount)
            bans:  json \(jsonByteCount.bans) feat \(banFeatureCount)
            pois:  json \(jsonByteCount.pois) feat \(poiFeatureCount) (sh \(poiShelterCount) fp \(poiFireplaceCount) ot \(poiOtherCount) nc \(poiAccommodationCount) wd \(poiViewpointCount) pk \(poiParkingCount) ed \(poiEducationCount))
            """
            // `@Published` always emits on assignment, so writing back an unchanged
            // string here would trigger a SwiftUI re-render loop that freezes the
            // app. Only push new text through.
            if text != lastDiagnosticsText {
                lastDiagnosticsText = text
                onDiagnostics(text)
            }
        }

        private func fmt(_ t: TimeInterval?) -> String {
            guard let t = t else { return "-" }
            return String(format: "%.1f", t)
        }

        /// Resets the freeze meter whenever the A/B configuration changes, so
        /// `stall=` always reflects the CURRENT state on the diagnostics line
        /// instead of the whole app session.
        private func resetStallMeter() {
            maxStall = 0
            lastStallProbe = Date()
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

        // MARK: Tap handling

        /// Vector overlays hit-test natively through `visibleFeatures`; the raster
        /// overlay hits against the retained catalog (nearest POI first, then
        /// point-in-polygon over zones and bans — Android parity order).
        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = mapView else {
                onTapBackground()
                return
            }
            let point = gesture.location(in: mapView)
            let tapRect = CGRect(x: point.x - 30, y: point.y - 30, width: 60, height: 60)
            if vectorOverlay {
                handleVectorTap(mapView, tapRect)
                return
            }
            guard let catalog = catalog else {
                onTapBackground()
                return
            }
            let coord = mapView.convert(point, toCoordinateFrom: mapView)
            let zoom = mapView.zoomLevel
            let degPerPixel = (360.0 / pow(2.0, zoom)) / 256.0
            let maxDeg = 30.0 * degPerPixel

            if let poi = OverlayRasterizer.nearestPoi(in: catalog,
                                                      lon: coord.longitude,
                                                      lat: coord.latitude,
                                                      maxDeg: maxDeg) {
                onTapPoi(poi.name)
                return
            }

            for zone in catalog.zones
            where OverlayRasterizer.polygon(zone, contains: coord.longitude, lat: coord.latitude) {
                onTapZone(zone.properties["name"] as? String)
                return
            }

            for ban in catalog.bans
            where OverlayRasterizer.polygon(ban, contains: coord.longitude, lat: coord.latitude) {
                if let id = (ban.properties["remoteId"] as? NSNumber)?.int64Value {
                    onTapBan(id)
                }
                return
            }

            onTapBackground()
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