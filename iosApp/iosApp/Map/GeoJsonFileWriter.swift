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
        let waterURL: URL
        let accommodationURL: URL
        let restURL: URL
        let shelterURL: URL
        let fireplaceURL: URL
        let viewpointURL: URL
        let waterLaunchURL: URL
        let parkingURL: URL
        let educationURL: URL
        let otherURL: URL
    }

    /// Writes `zones`/`bans`/`water` verbatim and splits `pois` by
    /// `categoryKey` into files, so each layer can be toggled by `isVisible`
    /// alone. Returns nil if any input is not a valid FeatureCollection or a
    /// file write fails.
    static func makeFiles(zones zonesJson: String,
                          bans bansJson: String,
                          pois poisJson: String,
                          water waterJson: String) -> Files? {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory
        let zonesURL = dir.appendingPathComponent("zwl-zones.json")
        let bansURL = dir.appendingPathComponent("zwl-bans.json")
        let waterURL = dir.appendingPathComponent("zwl-water.json")

        guard let zonesData = zonesJson.data(using: .utf8),
              let zonesObject = try? JSONSerialization.jsonObject(with: zonesData) as? [String: Any],
              zonesObject["features"] != nil,
              (try? zonesData.write(to: zonesURL)) != nil,
              let bansData = bansJson.data(using: .utf8),
              let bansObject = try? JSONSerialization.jsonObject(with: bansData) as? [String: Any],
              bansObject["features"] != nil,
              (try? bansData.write(to: bansURL)) != nil,
              let waterData = waterJson.data(using: .utf8),
              let waterObject = try? JSONSerialization.jsonObject(with: waterData) as? [String: Any],
              (waterObject["features"] as? [[String: Any]]) != nil,
              (try? waterData.write(to: waterURL)) != nil,
              let poisData = poisJson.data(using: .utf8),
              let poisObject = try? JSONSerialization.jsonObject(with: poisData) as? [String: Any],
              let poiFeatures = poisObject["features"] as? [[String: Any]] else {
            return nil
        }

        var accommodation: [[String: Any]] = []
        var rest: [[String: Any]] = []
        var shelter: [[String: Any]] = []
        var fireplace: [[String: Any]] = []
        var viewpoint: [[String: Any]] = []
        var waterLaunch: [[String: Any]] = []
        var parking: [[String: Any]] = []
        var education: [[String: Any]] = []
        var other: [[String: Any]] = []
        for feature in poiFeatures {
            let key = (feature["properties"] as? [String: Any])?["categoryKey"] as? String ?? "inne"
            switch key {
            case "noclegi": accommodation.append(feature)
            case "wypoczynek": rest.append(feature)
            case "wiaty": shelter.append(feature)
            case "ogniska": fireplace.append(feature)
            case "widoki": viewpoint.append(feature)
            case "wodowanie": waterLaunch.append(feature)
            case "parkingi": parking.append(feature)
            case "edukacja": education.append(feature)
            default: other.append(feature)
            }
        }

        let accommodationURL = dir.appendingPathComponent("zwl-pois-noclegi.json")
        let restURL = dir.appendingPathComponent("zwl-pois-wypoczynek.json")
        let shelterURL = dir.appendingPathComponent("zwl-pois-wiaty.json")
        let fireplaceURL = dir.appendingPathComponent("zwl-pois-ogniska.json")
        let viewpointURL = dir.appendingPathComponent("zwl-pois-widoki.json")
        let waterLaunchURL = dir.appendingPathComponent("zwl-pois-wodowanie.json")
        let parkingURL = dir.appendingPathComponent("zwl-pois-parkingi.json")
        let educationURL = dir.appendingPathComponent("zwl-pois-edukacja.json")
        let otherURL = dir.appendingPathComponent("zwl-pois-inne.json")
        guard write(features: accommodation, to: accommodationURL),
              write(features: rest, to: restURL),
              write(features: shelter, to: shelterURL),
              write(features: fireplace, to: fireplaceURL),
              write(features: viewpoint, to: viewpointURL),
              write(features: waterLaunch, to: waterLaunchURL),
              write(features: parking, to: parkingURL),
              write(features: education, to: educationURL),
              write(features: other, to: otherURL) else {
            return nil
        }

        return Files(zonesURL: zonesURL,
                     bansURL: bansURL,
                     waterURL: waterURL,
                     accommodationURL: accommodationURL,
                     restURL: restURL,
                     shelterURL: shelterURL,
                     fireplaceURL: fireplaceURL,
                     viewpointURL: viewpointURL,
                     waterLaunchURL: waterLaunchURL,
                     parkingURL: parkingURL,
                     educationURL: educationURL,
                     otherURL: otherURL)
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
