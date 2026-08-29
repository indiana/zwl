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
        private var layersReady = false
        private var zoneFeatureCount = 0
        private var banFeatureCount = 0
        private var poiFeatureCount = 0
        private var poiShelterCount = 0
        private var poiFireplaceCount = 0
        private var poiOtherCount = 0
        private var jsonByteCount = (zones: 0, bans: 0, pois: 0)
        private var zoneSource: MLNShapeSource?
        private var banSource: MLNShapeSource?
        private var poiSource: MLNShapeSource?
        private var hasCenteredOnStartup = false
        private var lastRecenterSignal = 0

        // Cached parsed features so `updateUIView` (fired on every observable
        // change) never re-parses the whole GeoJSON on the main thread.
        private var parsedZoneFeatures: [MLNShape] = []
        private var parsedBanFeatures: [MLNShape] = []
        private var parsedPoiFeatures: [MLNShape] = []
        private var lastJsonSignature = ""
        private var lastToggleSignature = ""
        private var lastDiagnosticsText = ""
        private var parseTask: Task<Void, Never>?

        private let zoneFillId = "zone-fill-layer"
        private let zoneLineId = "zone-line-layer"
        private let banFillId = "ban-fill-layer"
        private let banLineId = "ban-line-layer"
        private let poiCircleId = "poi-circle-layer"

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
            styleFinishCount += 1
            setupLayers(on: mapView)
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

        // MARK: Layers

        private func setupLayers(on mapView: MLNMapView) {
            guard let style = mapView.style else { return }
            removeLayerIfPresent(zoneFillId, style: style)
            removeLayerIfPresent(zoneLineId, style: style)
            removeLayerIfPresent(banFillId, style: style)
            removeLayerIfPresent(banLineId, style: style)
            removeLayerIfPresent(poiCircleId, style: style)
            removeSourceIfPresent("zone-source", style: style)
            removeSourceIfPresent("ban-source", style: style)
            removeSourceIfPresent("poi-source", style: style)

            let zoneSource = MLNShapeSource(identifier: "zone-source", features: [], options: nil)
            style.addSource(zoneSource)
            self.zoneSource = zoneSource

            let zoneFill = MLNFillStyleLayer(identifier: zoneFillId, source: zoneSource)
            zoneFill.fillColor = NSExpression(forConstantValue: UIColor(red: 0.10, green: 0.65, blue: 0.25, alpha: 0.35))
            zoneFill.fillOutlineColor = NSExpression(forConstantValue: UIColor(red: 0.0, green: 0.4, blue: 0.1, alpha: 1.0))
            // Antialiased fills are a big fragment cost for many-polygon
            // sources on older devices; the overlay look barely notices it off.
            zoneFill.fillAntialiased = NSExpression(forConstantValue: false)
            style.addLayer(zoneFill)

            let zoneLine = MLNLineStyleLayer(identifier: zoneLineId, source: zoneSource)
            zoneLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.0, green: 0.45, blue: 0.1, alpha: 0.9))
            zoneLine.lineWidth = NSExpression(forConstantValue: 2.0)
            style.addLayer(zoneLine)

            let banSource = MLNShapeSource(identifier: "ban-source", features: [], options: nil)
            style.addSource(banSource)
            self.banSource = banSource

            let banFill = MLNFillStyleLayer(identifier: banFillId, source: banSource)
            banFill.fillColor = NSExpression(forConstantValue: UIColor(red: 0.8, green: 0.1, blue: 0.1, alpha: 0.3))
            banFill.fillOutlineColor = NSExpression(forConstantValue: UIColor(red: 0.6, green: 0.0, blue: 0.0, alpha: 1.0))
            banFill.fillAntialiased = NSExpression(forConstantValue: false)
            style.addLayer(banFill)

            let banLine = MLNLineStyleLayer(identifier: banLineId, source: banSource)
            banLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.7, green: 0.05, blue: 0.05, alpha: 0.9))
            banLine.lineWidth = NSExpression(forConstantValue: 2.0)
            style.addLayer(banLine)

            // One POI source, three circle layers filtered by `categoryKey` so
            // toggling visibility never rebuilds/refilters the data.
            let poiSource = MLNShapeSource(identifier: "poi-source", features: [], options: nil)
            style.addSource(poiSource)
            self.poiSource = poiSource

            let poiCircle = MLNCircleStyleLayer(identifier: poiCircleId, source: poiSource)
            // Zoom-scaled radius, mirroring Android: dots shrink when zoomed out
            // so a whole-country view does not collapse into a blob.
            poiCircle.circleRadius = NSExpression(
                forFunction: "mgl_step",
                arguments: [
                    NSExpression(forKeyPath: "zoom"),
                    NSExpression(forConstantValue: 2.0),
                    NSExpression(forConstantValue: 11.0),
                    NSExpression(forConstantValue: 3.0),
                    NSExpression(forConstantValue: 12.5),
                    NSExpression(forConstantValue: 5.0),
                    NSExpression(forConstantValue: 14.0),
                    NSExpression(forConstantValue: 8.0),
                    NSExpression(forConstantValue: 16.0),
                    NSExpression(forConstantValue: 12.0)
                ]
            )
            poiCircle.circleColor = colorExpression()
            poiCircle.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            poiCircle.circleStrokeWidth = NSExpression(forConstantValue: 1.5)
            style.addLayer(poiCircle)

            style.layer(withIdentifier: banFillId)?.isVisible = showBans
            style.layer(withIdentifier: banLineId)?.isVisible = showBans

            layersReady = true
        }

        private func colorExpression() -> NSExpression {
            let shelterColor = NSExpression(forConstantValue: UIColor(red: 0.10, green: 0.65, blue: 0.25, alpha: 1.0))
            let fireplaceColor = NSExpression(forConstantValue: UIColor.systemOrange)
            let fallbackColor = NSExpression(forConstantValue: UIColor.systemBlue)
            let matched = [
                NSExpression(forConstantValue: "shelter"): shelterColor,
                NSExpression(forConstantValue: "fireplace"): fireplaceColor
            ]
            return NSExpression(forMLNMatchingKey: NSExpression(forKeyPath: "categoryKey"),
                                in: matched,
                                default: fallbackColor)
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

        // MARK: Data application

        func applySourcesIfReady() {
            guard styleLoaded, mapView != nil else { return }
            scheduleParseIfNeeded()
            refreshLayerVisibility()
            publishDiagnostics()
        }

        /// Kicks off parsing on a background QoS, once per data change. The
        /// heavy GeoJSON decode + simplification must never run on the main
        /// thread (it previously froze the app for seconds at first open).
        private func scheduleParseIfNeeded() {
            let jsonSignature = "\(zonesJson.count)|\(bansJson.count)|\(poisJson.count)"
            guard jsonSignature != lastJsonSignature else { return }
            lastJsonSignature = jsonSignature
            jsonByteCount = (zones: zonesJson.count,
                             bans: bansJson.count,
                             pois: poisJson.count)

            let zones = zonesJson
            let bans = bansJson
            let pois = poisJson
            parseTask?.cancel()
            parseTask = Task.detached(priority: .utility) { [weak self] in
                let zoneFeatures = GeoJsonToFeatures.features(from: zones)
                let banFeatures = GeoJsonToFeatures.features(from: bans)
                let poiFeatures = GeoJsonToFeatures.features(from: pois)
                let shel = poiFeatures.filter { GeoJsonToFeatures.categoryKey(of: $0) == "shelter" }.count
                let fire = poiFeatures.filter { GeoJsonToFeatures.categoryKey(of: $0) == "fireplace" }.count
                let other = poiFeatures.count - shel - fire
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self?.applyParsed(zones: zoneFeatures,
                                      bans: banFeatures,
                                      pois: poiFeatures,
                                      shelter: shel,
                                      fireplace: fire,
                                      other: other)
                }
            }
        }

        private func applyParsed(zones: [MLNShape],
                                 bans: [MLNShape],
                                 pois: [MLNShape],
                                 shelter: Int,
                                 fireplace: Int,
                                 other: Int) {
            parsedZoneFeatures = zones
            zoneFeatureCount = zones.count
            zoneSource?.shape = MLNShapeCollectionFeature(shapes: zones)

            parsedBanFeatures = bans
            banFeatureCount = bans.count
            banSource?.shape = MLNShapeCollectionFeature(shapes: bans)

            parsedPoiFeatures = pois
            poiFeatureCount = pois.count
            poiShelterCount = shelter
            poiFireplaceCount = fireplace
            poiOtherCount = other

            // Fresh data was just applied: rebuild the filtered POI collection
            // once (it would otherwise be skipped because toggles didn't move).
            lastToggleSignature = ""
            refreshLayerVisibility()
            publishDiagnostics()
        }

        /// Rebuilds the (filtered) POI collection and syncs ban layer
        /// visibility, but ONLY when a toggle actually changed. Rebuilding on
        /// every updateUIView made MapLibre re-ingest the data per frame and
        /// stutter the scroll.
        private func refreshLayerVisibility() {
            guard let style = mapView?.style else { return }
            let signature = "\(showBans)|\(showShelters)|\(showFireplaces)|\(showOthers)"
            guard signature != lastToggleSignature else { return }
            lastToggleSignature = signature

            let filteredPois = parsedPoiFeatures.filter { feature in
                switch GeoJsonToFeatures.categoryKey(of: feature) {
                case "shelter": return showShelters
                case "fireplace": return showFireplaces
                default: return showOthers
                }
            }
            poiSource?.shape = MLNShapeCollectionFeature(shapes: filteredPois)

            style.layer(withIdentifier: banFillId)?.isVisible = showBans
            style.layer(withIdentifier: banLineId)?.isVisible = showBans
        }

        // MARK: Diagnostics

        private func publishDiagnostics() {
            let styleState = styleLoaded ? "YES(\(styleFinishCount))" : "NO"
            let fail = styleErrorText.isEmpty ? "-" : styleErrorText
            let layersState = layersReady ? "YES" : "NO"
            let text = """
            style=\(styleState) fail=\(fail) layers=\(layersState)
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
            mapView.userTrackingMode = .follow
            let bounds = mapView.visibleCoordinateBounds
            onVisibleRegionChange(
                MapRegion(latSouth: bounds.sw.latitude,
                          latNorth: bounds.ne.latitude,
                          lonWest: bounds.sw.longitude,
                          lonEast: bounds.ne.longitude)
            )
        }

        // MARK: Tap handling

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView = mapView else { return }
            let point = gesture.location(in: mapView)
            let tapRect = CGRect(x: point.x - 30, y: point.y - 30, width: 60, height: 60)

            let pois = mapView.visibleFeatures(in: tapRect, styleLayerIdentifiers: [poiCircleId])
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