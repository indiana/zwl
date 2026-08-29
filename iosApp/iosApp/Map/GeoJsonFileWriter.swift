import Foundation

/// Splits the shared GeoJSON strings into on-disk FeatureCollection files so
/// the map can feed them to MapLibre through its URL-based GeoJSON tile
/// pipeline (worker-thread tiling, like Android's `GeoJsonSource`). The
/// runtime-shape path (`MLNShapeSource(features:)`) tessellated everything on
/// the main thread, which is what made the iOS map sluggish.
enum GeoJsonFileWriter {

    struct Files {
        let zonesURL: URL
        let bansURL: URL
        let shelterURL: URL
        let fireplaceURL: URL
        let otherURL: URL
        let zoneCount: Int
        let banCount: Int
        let shelterCount: Int
        let fireplaceCount: Int
        let otherCount: Int
    }

    /// Writes `zones`/`bans` verbatim and splits `pois` by `categoryKey` into
    /// three files (shelter / fireplace / other), so each layer can be toggled
    /// by `isVisible` alone. Returns nil if any input is not a valid
    /// FeatureCollection or a file write fails.
    static func makeFiles(zones zonesJson: String,
                          bans bansJson: String,
                          pois poisJson: String) -> Files? {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory
        let zonesURL = dir.appendingPathComponent("zwl-zones.json")
        let bansURL = dir.appendingPathComponent("zwl-bans.json")

        guard let zonesData = zonesJson.data(using: .utf8),
              let zonesObject = try? JSONSerialization.jsonObject(with: zonesData) as? [String: Any],
              let zoneFeatures = zonesObject["features"] as? [[String: Any]],
              (try? zonesData.write(to: zonesURL)) != nil,
              let bansData = bansJson.data(using: .utf8),
              let bansObject = try? JSONSerialization.jsonObject(with: bansData) as? [String: Any],
              let banFeatures = bansObject["features"] as? [[String: Any]],
              (try? bansData.write(to: bansURL)) != nil,
              let poisData = poisJson.data(using: .utf8),
              let poisObject = try? JSONSerialization.jsonObject(with: poisData) as? [String: Any],
              let poiFeatures = poisObject["features"] as? [[String: Any]] else {
            return nil
        }

        var shelter: [[String: Any]] = []
        var fireplace: [[String: Any]] = []
        var other: [[String: Any]] = []
        for feature in poiFeatures {
            let key = (feature["properties"] as? [String: Any])?["categoryKey"] as? String ?? "other"
            switch key {
            case "shelter": shelter.append(feature)
            case "fireplace": fireplace.append(feature)
            default: other.append(feature)
            }
        }

        let shelterURL = dir.appendingPathComponent("zwl-pois-shelter.json")
        let fireplaceURL = dir.appendingPathComponent("zwl-pois-fireplace.json")
        let otherURL = dir.appendingPathComponent("zwl-pois-other.json")
        guard write(features: shelter, to: shelterURL),
              write(features: fireplace, to: fireplaceURL),
              write(features: other, to: otherURL) else {
            return nil
        }

        return Files(zonesURL: zonesURL,
                     bansURL: bansURL,
                     shelterURL: shelterURL,
                     fireplaceURL: fireplaceURL,
                     otherURL: otherURL,
                     zoneCount: zoneFeatures.count,
                     banCount: banFeatures.count,
                     shelterCount: shelter.count,
                     fireplaceCount: fireplace.count,
                     otherCount: other.count)
    }

    private static func write(features: [[String: Any]], to url: URL) -> Bool {
        do {
            let data = try JSONSerialization.data(withJSONObject: [
                "type": "FeatureCollection",
                "features": features
            ])
            try data.write(to: url, options: .atomic)
            return true
        } catch {
            return false
        }
    }
}