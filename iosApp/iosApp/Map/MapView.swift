import SwiftUI
import MapLibre
import CoreLocation
import shared

struct MapView: UIViewRepresentable {

    let zonesJson: String
    let bansJson: String
    let poisJson: String
    let showBans: Bool
    let showShelters: Bool
    let showFireplaces: Bool
    let showOthers: Bool
    let overlayEnabled: Bool
    let followsUser: Bool
    let showHeading: Bool
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
        map.prefetchesTiles = false
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
            zoomLevel: MapStyle.shared.DEFAULT_ZOOM,
            animated: false
        )

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        map.addGestureRecognizer(tap)

        context.coordinator.scheduleStyleProbe()
        context.coordinator.startStallProbe()
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.zonesJson = zonesJson
        coordinator.bansJson = bansJson
        coordinator.poisJson = poisJson
        coordinator.showBans = showBans
        coordinator.showShelters = showShelters
        coordinator.showFireplaces = showFireplaces
        coordinator.showOthers = showOthers
        coordinator.overlayEnabled = overlayEnabled
        coordinator.followsUser = followsUser
        coordinator.showHeading = showHeading
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
        var showShelters = true
        var showFireplaces = true
        var showOthers = true
        var overlayEnabled = true {
            didSet {
                guard oldValue != overlayEnabled else { return }
                resetStallMeter()
                applyOverlayEnabled()
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
        private var layersReady = false
        private var layersApplied = false
        private var zoneFeatureCount = 0
        private var banFeatureCount = 0
        private var poiFeatureCount = 0
        private var poiShelterCount = 0
        private var poiFireplaceCount = 0
        private var poiOtherCount = 0
        private var jsonByteCount = (zones: 0, bans: 0, pois: 0)
        private var hasCenteredOnStartup = false
        private var lastRecenterSignal = 0
        private var lastDiagnosticsText = ""

        // On-disk GeoJSON files feeding the rasterizer. Written once per data
        // change on a background QoS; `updateUIView` re-runs are free.
        private var dataFiles: GeoJsonFileWriter.Files?
        private var lastJsonSignature = ""
        private var lastToggleSignature = ""

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
            scheduleRasterBuildFromCamera()
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
            guard overlayEnabled, let map = mapView, dataFiles != nil else { return }
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
            guard overlayEnabled else { return }
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

        /// Diagnostics A/B: fully detaches (or re-attaches) the raster overlay
        /// while the app keeps running. The catalog and baked tiles survive on
        /// disk, so re-enabling re-installs the sources immediately.
        private func applyOverlayEnabled() {
            guard let style = mapView?.style else { return }
            if overlayEnabled {
                installRasterIfCatalogReady()
                scheduleRasterBuildFromCamera()
            } else {
                rasterTask?.cancel()
                rasterTask = nil
                writeTask?.cancel()
                writeTask = nil
                pendingBuild = nil
                layersApplied = false
                layersReady = false
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
                publishDiagnostics()
            }
        }

        // MARK: Data application

        func applySourcesIfReady() {
            guard styleLoaded, mapView != nil else { return }
            scheduleWriteIfNeeded()
            installRasterIfCatalogReady()
            refreshLayerVisibility()
            publishDiagnostics()
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
            zoneFeatureCount = files.zoneCount
            banFeatureCount = files.banCount
            poiFeatureCount = files.shelterCount + files.fireplaceCount + files.otherCount
            poiShelterCount = files.shelterCount
            poiFireplaceCount = files.fireplaceCount
            poiOtherCount = files.otherCount

            // New dataset: drop every baked tile so nothing stale can remain,
            // then rebuild for the current viewport in the background.
            rasterTask?.cancel()
            writeTask?.cancel()
            catalog = nil
            layersApplied = false
            layersReady = false
            builtZoom = -1
            pendingBuild = nil
            OverlayRasterizer.reset()
            scheduleRasterBuildFromCamera()
            publishDiagnostics()
        }

        /// Toggles switch raster opacity only — the cheapest style mutation
        /// there is. No geometry, no data rebuild, no filter pass.
        private func refreshLayerVisibility() {
            guard layersReady, let style = mapView?.style else { return }
            let signature = "\(showBans)|\(showShelters)|\(showFireplaces)|\(showOthers)"
            guard signature != lastToggleSignature else { return }
            lastToggleSignature = signature

            setRasterOpacity(banRasterId, visible: showBans, style: style)
            setRasterOpacity(shelterRasterId, visible: showShelters, style: style)
            setRasterOpacity(fireplaceRasterId, visible: showFireplaces, style: style)
            setRasterOpacity(otherRasterId, visible: showOthers, style: style)
        }

        private func setRasterOpacity(_ id: String, visible: Bool, style: MLNStyle) {
            if let layer = style.layer(withIdentifier: id) as? MLNRasterStyleLayer {
                layer.rasterOpacity = NSExpression(forConstantValue: visible ? 1.0 : 0.0)
            }
        }

        // MARK: Diagnostics

        private func publishDiagnostics() {
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
            } else if layersReady {
                overlayText = "tiles=\(tiles)/\(done) z=\(zMin)-\(zMax) skip=\(skippedBuildCount)"
            } else {
                overlayText = "pending files=\(dataFiles != nil) q=\(pendingBuild?.zoom ?? -1) busy=\(rasterTask != nil) skip=\(skippedBuildCount)"
            }
            let text = """
            style=\(styleState) fail=\(fail) layers=\(layersState) stall=\(String(format: "%.1f", maxStall))s
            overlay: \(overlayText)
            zones: json \(jsonByteCount.zones) feat \(zoneFeatureCount)
            bans:  json \(jsonByteCount.bans) feat \(banFeatureCount)
            pois:  json \(jsonByteCount.pois) feat \(poiFeatureCount) (sh \(poiShelterCount) fp \(poiFireplaceCount) ot \(poiOtherCount))
            """
            // `@Published` always emits on assignment, so writing back an unchanged
            // string here would trigger a SwiftUI re-render loop that freezes the
            // app. Only push new text through.
            if text != lastDiagnosticsText {
                lastDiagnosticsText = text
                onDiagnostics(text)
            }
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

        /// Overlays are rasters now, so `visibleFeatures` has nothing to offer.
        /// Hit-testing runs against the retained catalog: nearest POI first,
        /// then point-in-polygon over zones and bans (Android parity order).
        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = mapView, let catalog = catalog else {
                onTapBackground()
                return
            }
            let point = gesture.location(in: mapView)
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
    }
}