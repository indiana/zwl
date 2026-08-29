import Foundation
import UIKit
import CoreGraphics
import ImageIO

/// Renders the zone / ban / POI overlays to local PNG web-mercator tiles so the
/// map only ever composites cheap rasters instead of tessellating ~18k vector
/// features on the main (render) thread. Tap hit-testing keeps working because
/// the parsed features are retained in the returned catalog.
///
/// Everything here runs off-main (build/topUp are called from a detached
/// utility task). The baked images mirror the previous layers 1:1:
///   zones  – green translucent fills + dark-green outlines
///   bans   – red translucent fills + red outlines
///   pois   – shelter (green), fireplace (orange), other (blue), zoom-scaled
enum OverlayRasterizer {

    // MARK: Layer identities (shared with MapView)

    static let zoneSourceId = "zone-raster"
    static let banSourceId = "ban-raster"
    static let shelterSourceId = "poi-shelter-raster"
    static let fireplaceSourceId = "poi-fireplace-raster"
    static let otherSourceId = "poi-other-raster"

    // MARK: Feature model

    struct OverlayPolygon {
        let rings: [[(lon: Double, lat: Double)]]
        let bbox: (minX: Double, minY: Double, maxX: Double, maxY: Double)
        let properties: [String: Any]
    }

    struct OverlayPoint {
        let lon: Double
        let lat: Double
        let properties: [String: Any]
    }

    /// Immutable snapshot of one raster build. Retained by the coordinator for
    /// tap hit-testing and diagnostics.
    final class Catalog {
        let zones: [OverlayPolygon]
        let bans: [OverlayPolygon]
        let pois: [OverlayPoint]
        let zMin: Int
        let zMax: Int
        let writtenTiles: Int
        let tilesDone: Int
        let dirURL: URL

        init(zones: [OverlayPolygon], bans: [OverlayPolygon], pois: [OverlayPoint],
             zMin: Int, zMax: Int, writtenTiles: Int, tilesDone: Int, dirURL: URL) {
            self.zones = zones
            self.bans = bans
            self.pois = pois
            self.zMin = zMin
            self.zMax = zMax
            self.writtenTiles = writtenTiles
            self.tilesDone = tilesDone
            self.dirURL = dirURL
        }
    }

    struct BuildResult {
        let catalog: Catalog
        let deltaWritten: Int
    }

    // MARK: Public API

    /// Wipes the overlay directory. Call before (re)building when the raw data
    /// changed so stale tiles from a previous dataset can never be served.
    static func reset() {
        let dir = baseDir()
        try? FileManager.default.removeItem(at: dir)
    }

    /// Number of tiles (per layer) that `renderTile` would produce for a
    /// region at an integer zoom. Used by the coordinator to skip pathological
    /// builds (e.g. a window-sized region at very high zoom before it settled).
    static func tileCount(region: Region, zoom: Int) -> Int {
        let r = tileRange(region: region, zoom: zoom)
        guard r.maxX >= r.minX, r.maxY >= r.minY else { return 0 }
        return (r.maxX - r.minX + 1) * (r.maxY - r.minY + 1)
    }

    /// Renders the current integer zoom for the given region across all five
    /// layers. Existing tiles are skipped; the region is the visible viewport,
    /// so this stays at ~hundreds of tiles. Neighbor zooms are top-ups, not
    /// pyramids.
    static func build(files: GeoJsonFileWriter.Files,
                      region: Region,
                      zMin: Int,
                      zMax: Int) -> BuildResult {
        let fm = FileManager.default
        let dir = baseDir()
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)

        let zones = parsePolygons(url: files.zonesURL)
        let bans = parsePolygons(url: files.bansURL)
        let pois = parsePoints(url: files.shelterURL)
            + parsePoints(url: files.fireplaceURL)
            + parsePoints(url: files.otherURL)

        var targetZooms: [Int] = []
        let lo = max(zMin, 2)
        let hi = max(lo, min(zMax, 20))
        if lo <= hi { targetZooms = Array(lo...hi) }

        var written = 0
        var attempted = 0

        let layers: [(layer: Layer, features: [Any])] = [
            (.zones, zones), (.bans, bans),
            (.shelters, pois), (.fireplaces, pois), (.others, pois)
        ]

        for z in targetZooms {
            let range = tileRange(region: region, zoom: z)
            guard range.maxX >= range.minX, range.maxY >= range.minY else { continue }
            for tileX in range.minX...range.maxX {
                for tileY in range.minY...range.maxY {
                    for entry in layers {
                        let url = tileURL(z: z, x: tileX, y: tileY, layer: entry.layer, dir: dir)
                        if fm.fileExists(atPath: url.path) { continue }
                        attempted += 1
                        if renderTile(layer: entry.layer, features: entry.features,
                                      z: z, x: tileX, y: tileY, to: url) {
                            written += 1
                        }
                    }
                }
            }
        }

        let catalog = Catalog(zones: zones, bans: bans, pois: pois,
                              zMin: lo, zMax: hi,
                              writtenTiles: written, tilesDone: attempted,
                              dirURL: dir)
        return BuildResult(catalog: catalog, deltaWritten: written)
    }

    // MARK: Geometry

    struct Region {
        let latSouth: Double
        let latNorth: Double
        let lonWest: Double
        let lonEast: Double
    }

    static func worldX(lon: Double) -> Double { (lon + 180.0) / 360.0 }

    static func worldY(lat: Double) -> Double {
        let sinLat = sin(lat * .pi / 180.0)
        return 0.5 - 0.25 * log((1 + sinLat) / (1 - sinLat)) / .pi
    }

    private static func tileRange(region: Region, zoom: Int) -> (minX: Int, maxX: Int, minY: Int, maxY: Int) {
        let n = Double(1 << zoom)
        let w = max(0, min(0.9999, worldX(lon: region.lonWest)))
        let e = max(0, min(0.9999, worldX(lon: region.lonEast)))
        let s = max(0.0001, min(1, worldY(lat: region.latSouth)))
        let no = max(0.0001, min(1, worldY(lat: region.latNorth)))
        let minX = Int(floor(w * n))
        let maxX = Int(floor((e - 0.000001) * n))
        let minY = Int(floor(min(s, no) * n))
        let maxY = Int(floor(max(s, no) * n))
        return (minX, maxX, minY, maxY)
    }

    // MARK: Parsing

    private static func parsePolygons(url: URL) -> [OverlayPolygon] {
        guard let data = try? Data(contentsOf: url),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = object["features"] as? [[String: Any]] else {
            return []
        }
        var result: [OverlayPolygon] = []
        for feature in features {
            guard let geom = feature["geometry"] as? [String: Any],
                  let type = geom["type"] as? String else { continue }
            let props = (feature["properties"] as? [String: Any]) ?? [:]
            switch type {
            case "Polygon":
                if let rings = parseRings(geom["coordinates"] as? [[[Double]]]) {
                    result.append(polygon(rings: rings, properties: props))
                }
            case "MultiPolygon":
                if let polys = geom["coordinates"] as? [[[[Double]]]] {
                    var rings: [[(lon: Double, lat: Double)]] = []
                    for poly in polys {
                        if let parsed = parseRings(poly) { rings.append(contentsOf: parsed) }
                    }
                    if !rings.isEmpty {
                        result.append(polygon(rings: rings, properties: props))
                    }
                }
            default: break
            }
        }
        return result
    }

    private static func parseRings(_ raw: [[[Double]]]?) -> [[(lon: Double, lat: Double)]]? {
        guard let raw = raw, !raw.isEmpty else { return nil }
        var rings: [[(lon: Double, lat: Double)]] = []
        for ring in raw {
            var points: [(lon: Double, lat: Double)] = []
            for pair in ring where pair.count >= 2 {
                points.append((lon: pair[0], lat: pair[1]))
            }
            if points.count >= 3 { rings.append(points) }
        }
        return rings.isEmpty ? nil : rings
    }

    private static func polygon(rings: [[(lon: Double, lat: Double)]], properties: [String: Any]) -> OverlayPolygon {
        var minX = Double.greatestFiniteMagnitude
        var minY = Double.greatestFiniteMagnitude
        var maxX = -Double.greatestFiniteMagnitude
        var maxY = -Double.greatestFiniteMagnitude
        for ring in rings {
            for (lon, lat) in ring {
                let x = worldX(lon: lon)
                let y = worldY(lat: lat)
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        return OverlayPolygon(rings: rings,
                              bbox: (minX: minX, minY: minY, maxX: maxX, maxY: maxY),
                              properties: properties)
    }

    private static func parsePoints(url: URL) -> [OverlayPoint] {
        guard let data = try? Data(contentsOf: url),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = object["features"] as? [[String: Any]] else {
            return []
        }
        var result: [OverlayPoint] = []
        for feature in features {
            guard let geom = feature["geometry"] as? [String: Any],
                  geom["type"] as? String == "Point",
                  let coords = geom["coordinates"] as? [Double], coords.count >= 2 else { continue }
            result.append(OverlayPoint(lon: coords[0], lat: coords[1],
                                       properties: (feature["properties"] as? [String: Any]) ?? [:]))
        }
        return result
    }

    // MARK: Rendering

    enum Layer: String, CaseIterable {
        case zones, bans, shelters, fireplaces, others

        var sourceId: String {
            switch self {
            case .zones: return zoneSourceId
            case .bans: return banSourceId
            case .shelters: return shelterSourceId
            case .fireplaces: return fireplaceSourceId
            case .others: return otherSourceId
            }
        }

        var fillColor: (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
            switch self {
            case .zones: return (0.10, 0.65, 0.25, 0.35)
            case .bans: return (0.80, 0.10, 0.10, 0.30)
            case .shelters: return (0.10, 0.65, 0.25, 1.0)
            case .fireplaces: return (1.00, 0.58, 0.00, 1.0)
            case .others: return (0.00, 0.48, 1.00, 1.0)
            }
        }

        var strokeColor: (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat)? {
            switch self {
            case .zones: return (0.10, 0.40, 0.10, 1.0)
            case .bans: return (0.60, 0.00, 0.00, 1.0)
            case .shelters, .fireplaces, .others: return nil
            }
        }

        var fill: Bool {
            switch self {
            case .zones, .bans: return true
            case .shelters, .fireplaces, .others: return false
            }
        }
    }

    private static func poiRadius(zoom: Int) -> CGFloat {
        switch zoom {
        case ..<11: return 2
        case ..<13: return 3
        case ..<14: return 5
        case ..<16: return 8
        default: return 12
        }
    }

    // MARK: Hit-testing (taps on the rasterized overlays)

    /// Ray-cast (even-odd) containment over all rings — holes included.
    static func polygon(_ polygon: OverlayPolygon, contains lon: Double, lat: Double) -> Bool {
        var inside = false
        for ring in polygon.rings {
            var j = ring.count - 1
            var hit = false
            for i in 0..<ring.count {
                let (xi, yi) = ring[i]
                let (xj, yj) = ring[j]
                if (yi > lat) != (yj > lat),
                   lon < (xj - xi) * (lat - yi) / (yj - yi) + xi {
                    hit.toggle()
                }
                j = i
            }
            if hit { inside.toggle() }
        }
        return inside
    }

    /// Nearest POI within `maxDeg` of the coordinate, or nil. Uses the POI
    /// ordering from the catalog (shelters, fireplaces, others), matching the
    /// old layer-priority tap order.
    static func nearestPoi(in catalog: Catalog, lon: Double, lat: Double,
                           maxDeg: Double) -> (name: String, distance: Double)? {
        var best: (name: String, distance: Double)? = nil
        for point in catalog.pois {
            let dLat = point.lat - lat
            let dLon = point.lon - lon
            let d = (dLon * dLon + dLat * dLat).squareRoot()
            guard d <= maxDeg else { continue }
            if best == nil || d < best!.distance {
                let name = (point.properties["name"] as? String) ?? ""
                best = (name, d)
            }
        }
        return best
    }

    private static func renderTile(layer: Layer, features: [Any],
                                   z: Int, x: Int, y: Int, to url: URL) -> Bool {
        let scale = CGFloat(1 << z)
        let worldMinX = Double(x) / Double(scale)
        let worldMinY = Double(y) / Double(scale)
        let worldMaxX = worldMinX + 1.0 / Double(scale)
        let worldMaxY = worldMinY + 1.0 / Double(scale)

        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1
        fmt.opaque = false
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 256, height: 256), format: fmt)
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
            cg.clear(CGRect(x: 0, y: 0, width: 256, height: 256))
            cg.setShouldAntialias(true)

            if layer.fill {
                // Zones / bans: translucent fill + outline clipped to the tile.
                guard let polys = features as? [OverlayPolygon] else { return }
                var paths: [CGPath] = []
                for poly in polys {
                    let bbox = poly.bbox
                    if bbox.maxX < worldMinX || bbox.minX > worldMaxX
                        || bbox.maxY < worldMinY || bbox.minY > worldMaxY { continue }
                    if let ring = polygonPath(rings: poly.rings, z: z, x: x, y: y) {
                        paths.append(ring)
                    }
                }
                guard !paths.isEmpty else { return }

                cg.saveGState()
                cg.clip(to: CGRect(x: 0, y: 0, width: 256, height: 256))
                let fill = layer.fillColor
                cg.setFillColor(UIColor(red: fill.r, green: fill.g, blue: fill.b, alpha: fill.a).cgColor)
                let fillPath = CGMutablePath()
                for ring in paths { fillPath.addPath(ring) }
                cg.addPath(fillPath)
                cg.drawPath(using: .fill)

                if let stroke = layer.strokeColor {
                    cg.setStrokeColor(UIColor(red: stroke.r, green: stroke.g, blue: stroke.b, alpha: stroke.a).cgColor)
                    cg.setLineWidth(1.5)
                    cg.setLineJoin(.round)
                    let outline = CGMutablePath()
                    for ring in paths { outline.addPath(ring) }
                    cg.addPath(outline)
                    cg.strokePath()
                }
                cg.restoreGState()
            } else {
                // POIs: zoom-scaled dots.
                guard let points = features as? [OverlayPoint] else { return }
                let radius = poiRadius(zoom: z)
                if radius < 2 { return }
                let rad = Double(radius)
                let fill = layer.fillColor
                let color = UIColor(red: fill.r, green: fill.g, blue: fill.b, alpha: fill.a)
                for point in points {
                    let px = (worldX(lon: point.lon) * Double(scale)) * 256.0 - Double(x * 256)
                    let py = (worldY(lat: point.lat) * Double(scale)) * 256.0 - Double(y * 256)
                    guard px >= -rad, px <= 256.0 + rad,
                          py >= -rad, py <= 256.0 + rad else { continue }
                    let cx = CGFloat(px)
                    let cy = CGFloat(py)
                    let rect = CGRect(x: cx - radius, y: cy - radius,
                                      width: radius * 2, height: radius * 2)
                    cg.setFillColor(color.cgColor)
                    cg.fillEllipse(in: rect)
                    cg.setStrokeColor(UIColor.white.cgColor)
                    cg.setLineWidth(1.0)
                    cg.strokeEllipse(in: rect)
                }
            }
        }
        guard let data = image.pngData() else { return false }
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                    withIntermediateDirectories: true)
            try data.write(to: url, options: .atomic)
            return true
        } catch {
            return false
        }
    }

    private static func polygonPath(rings: [[(lon: Double, lat: Double)]], z: Int, x: Int, y: Int) -> CGPath? {
        let scale = Double(1 << z)
        let transform: (Double, Double) -> (CGFloat, CGFloat) = { lon, lat in
            (CGFloat(worldX(lon: lon) * scale) * 256.0 - CGFloat(x * 256),
             CGFloat(worldY(lat: lat) * scale) * 256.0 - CGFloat(y * 256))
        }
        var count = 0
        let path = CGMutablePath()
        for ring in rings {
            guard ring.count >= 3 else { continue }
            var (sx, sy) = transform(ring[0].lon, ring[0].lat)
            path.move(to: CGPoint(x: sx, y: sy))
            count += 1
            for pair in ring.dropFirst() {
                (sx, sy) = transform(pair.lon, pair.lat)
                path.addLine(to: CGPoint(x: sx, y: sy))
                count += 1
            }
            path.closeSubpath()
        }
        guard count > 0 else { return nil }
        return path
    }

    // MARK: Files

    private static func baseDir() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("zwl-overlay", isDirectory: true)
    }

    private static func tileURL(z: Int, x: Int, y: Int, layer: Layer, dir: URL) -> URL {
        dir.appendingPathComponent(layer.rawValue)
            .appendingPathComponent("\(z)")
            .appendingPathComponent("\(x)")
            .appendingPathComponent("\(y)")
            .appendingPathExtension("png")
    }

    /// file:// tile-URL template for a layer, e.g.
    /// `file:///.../zwl-overlay/zones/{z}/{x}/{y}.png`.
    static func tileTemplate(layer: Layer) -> String {
        baseDir().appendingPathComponent(layer.rawValue, isDirectory: true)
            .absoluteString + "{z}/{x}/{y}.png"
    }
}