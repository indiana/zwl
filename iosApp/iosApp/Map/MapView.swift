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
        context.coordinator.mapView = map
        map.setCenter(
            CLLocationCoordinate2D(latitude: MapStyle.shared.DEFAULT_LAT,
                                   longitude: MapStyle.shared.DEFAULT_LNG),
            zoomLevel: MapStyle.shared.DEFAULT_ZOOM,
            animated: false
        )

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        map.addGestureRecognizer(tap)
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

        private var styleLoaded = false
        private var zoneSource: MLNShapeSource?
        private var banSource: MLNShapeSource?
        private var poiSource: MLNShapeSource?
        private var hasCenteredOnStartup = false
        private var lastRecenterSignal = 0

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

        func mapViewDidFinishLoadingStyle(_ mapView: MLNMapView) {
            setupLayers(on: mapView)
            styleLoaded = true
            applySourcesIfReady()
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

        // MARK: Layers

        private func setupLayers(on mapView: MLNMapView) {
            guard let style = mapView.style else { return }
            removeLayerIfPresent(zoneFillId, style: style)
            removeLayerIfPresent(zoneLineId, style: style)
            removeLayerIfPresent(banFillId, style: style)
            removeLayerIfPresent(banLineId, style: style)
            removeLayerIfPresent(poiCircleId, style: style)

            let zoneSource = MLNShapeSource(identifier: "zone-source", features: [], options: nil)
            style.addSource(zoneSource)
            self.zoneSource = zoneSource

            let zoneFill = MLNFillStyleLayer(identifier: zoneFillId, source: zoneSource)
            zoneFill.fillColor = NSExpression(forConstantValue: UIColor(red: 0.10, green: 0.65, blue: 0.25, alpha: 0.35))
            zoneFill.fillOutlineColor = NSExpression(forConstantValue: UIColor(red: 0.0, green: 0.4, blue: 0.1, alpha: 1.0))
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
            style.addLayer(banFill)

            let banLine = MLNLineStyleLayer(identifier: banLineId, source: banSource)
            banLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.7, green: 0.05, blue: 0.05, alpha: 0.9))
            banLine.lineWidth = NSExpression(forConstantValue: 2.0)
            style.addLayer(banLine)

            let poiSource = MLNShapeSource(identifier: "poi-source", features: [], options: nil)
            style.addSource(poiSource)
            self.poiSource = poiSource

            let poiCircle = MLNCircleStyleLayer(identifier: poiCircleId, source: poiSource)
            poiCircle.circleRadius = NSExpression(forConstantValue: 6.0)
            poiCircle.circleColor = colorExpression()
            poiCircle.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            poiCircle.circleStrokeWidth = NSExpression(forConstantValue: 1.5)
            style.addLayer(poiCircle)
        }

        private func removeLayerIfPresent(_ id: String, style: MLNStyle) {
            if let layer = style.layer(withIdentifier: id) {
                style.removeLayer(layer)
            }
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

        // MARK: Data application

        func applySourcesIfReady() {
            guard styleLoaded, let mapView = mapView else { return }

            let zoneFeatures = GeoJsonToFeatures.features(from: zonesJson)
            zoneSource?.shape = MLNShapeCollectionFeature(shapes: zoneFeatures)

            let banFeatures = GeoJsonToFeatures.features(from: bansJson)
            banSource?.shape = MLNShapeCollectionFeature(shapes: banFeatures)

            let pois = GeoJsonToFeatures.features(from: poisJson)
            let filteredPois = pois.filter { feature in
                let key = (feature as? MLNFeature)?.attributes["categoryKey"] as? String ?? "other"
                switch key {
                case "shelter": return showShelters
                case "fireplace": return showFireplaces
                default: return showOthers
                }
            }
            poiSource?.shape = MLNShapeCollectionFeature(shapes: filteredPois)

            if let style = mapView.style {
                style.layer(withIdentifier: banFillId)?.isVisible = showBans
                style.layer(withIdentifier: banLineId)?.isVisible = showBans
            }
        }

        // MARK: Camera centering

        /// Centers the camera on the user position, once after the first fix and
        /// again on every explicit recenter request (Android parity).
        func handleCentering(userLatitude: Double?, userLongitude: Double?, recenterSignal: Int) {
            guard let mapView = mapView else { return }
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