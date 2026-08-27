import Foundation
import MapLibre
import CoreLocation

/// Turns the GeoJSON FeatureCollection strings produced by the shared module
/// into `MGLShape` features that can be fed to `MLNShapeSource`s.
enum GeoJsonToFeatures {

    static func features(from json: String) -> [MGLShape] {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let featureArray = obj["features"] as? [[String: Any]] else {
            return []
        }

        var result: [MGLShape] = []
        for feature in featureArray {
            guard let geometry = feature["geometry"] as? [String: Any],
                  let type = geometry["type"] as? String,
                  let coords = geometry["coordinates"] else { continue }
            let properties = feature["properties"] as? [String: Any] ?? [:]

            switch type {
            case "Polygon":
                if let rings = coords as? [[[Any]]] {
                    result.append(contentsOf: polygonFeatures(rings: rings, properties: properties))
                }
            case "MultiPolygon":
                if let polygons = coords as? [[[[Any]]]] {
                    for polygon in polygons {
                        result.append(contentsOf: polygonFeatures(rings: polygon, properties: properties))
                    }
                }
            case "Point":
                if let point = coords as? [NSNumber], point.count >= 2,
                   let lon = point[0].doubleValue as Double?,
                   let lat = point[1].doubleValue as Double? {
                    let feature = MGLPointFeature()
                    feature.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
                    feature.attributes = properties
                    result.append(feature)
                }
            default:
                break
            }
        }
        return result
    }

    private static func polygonFeatures(rings: [[[Any]]], properties: [String: Any]) -> [MGLShape] {
        guard let shellNumbered = rings.first else { return [] }
        let shellPoints = shellNumbered.compactMap { coordinate2D($0) }
        guard shellPoints.count >= 3 else { return [] }

        let polygon = shellPoints.withUnsafeBufferPointer { buffer -> MGLPolygonFeature in
            let feature = MGLPolygonFeature(coordinates: buffer.baseAddress!, count: buffer.count)
            feature.attributes = properties
            return feature
        }
        return [polygon]
    }

    /// Converts a GeoJSON coordinate pair `[lon, lat]` to `CLLocationCoordinate2D`.
    private static func coordinate2D(_ pair: [Any]) -> CLLocationCoordinate2D? {
        guard let lon = (pair.first as? NSNumber)?.doubleValue,
              pair.count >= 2,
              let lat = (pair[1] as? NSNumber)?.doubleValue else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}