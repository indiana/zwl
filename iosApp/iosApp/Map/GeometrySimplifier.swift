import Foundation
import CoreLocation

/// Memory/CPU saver for the map: zone and ban polygons are very dense (BDL
/// boundaries often have a vertex every few meters), which is why parsing and
/// MapLibre re-bucketing on the main thread was so slow with ~7k zones.
///
/// Douglas-Peucker runs after GeoJSON decode so the `MLNShape`s are born
/// light. The simplification is display-only: membership/in-zone checks live
/// in the shared Kotlin module and use the unmodified data. Tune `tolerance`
/// after QA screenshots — `0.0002`deg is ~22m and should be invisible at the
/// zoom levels used by the app.
enum GeometrySimplifier {

    static let tolerance: CLLocationDegrees = 0.0002

    /// Simplifies a closed polygon ring (first == last point). Returns the ring
    /// with the same closed invariant.
    static func simplifyRing(_ ring: [CLLocationCoordinate2D]) -> [CLLocationCoordinate2D] {
        guard ring.count > 4 else { return ring }
        let isClosed = ring.first == ring.last
        let interior = isClosed ? Array(ring.dropLast()) : ring
        guard interior.count >= 3 else { return ring }
        let simplified = douglasPeucker(interior, tolerance: tolerance)
        guard simplified.count >= 3 else { return ring }
        return isClosed ? simplified + [simplified[0]] : simplified
    }

    private static func douglasPeucker(_ points: [CLLocationCoordinate2D],
                                       tolerance: CLLocationDegrees) -> [CLLocationCoordinate2D] {
        guard points.count > 2 else { return points }
        var keep = [Bool](repeating: false, count: points.count)
        keep[0] = true
        keep[points.count - 1] = true
        var stack: [(start: Int, end: Int)] = [(0, points.count - 1)]

        while let (start, end) = stack.popLast() {
            guard end > start + 1 else { continue }
            let a = points[start]
            let b = points[end]
            var maxDistance = 0.0
            var maxIndex = -1
            for i in (start + 1)..<end {
                let d = perpendicularDistance(points[i], a, b)
                if d > maxDistance {
                    maxDistance = d
                    maxIndex = i
                }
            }
            if maxIndex > 0, maxDistance > tolerance {
                keep[maxIndex] = true
                stack.append((start, maxIndex))
                stack.append((maxIndex, end))
            }
        }

        var result: [CLLocationCoordinate2D] = []
        result.reserveCapacity(keep.count)
        for i in 0..<points.count where keep[i] {
            result.append(points[i])
        }
        return result
    }

    /// Perpendicular distance from `p` to the segment `a`-`b`, in degree space
    /// (fine for the small simplifications we apply).
    private static func perpendicularDistance(_ p: CLLocationCoordinate2D,
                                              _ a: CLLocationCoordinate2D,
                                              _ b: CLLocationCoordinate2D) -> Double {
        let x0 = p.longitude, y0 = p.latitude
        let x1 = a.longitude, y1 = a.latitude
        let x2 = b.longitude, y2 = b.latitude
        let dx = x2 - x1
        let dy = y2 - y1
        let lengthSquared = dx * dx + dy * dy
        if lengthSquared == 0 {
            let ex = x0 - x1, ey = y0 - y1
            return sqrt(ex * ex + ey * ey)
        }
        let t = ((x0 - x1) * dx + (y0 - y1) * dy) / lengthSquared
        let px = x1 + t * dx
        let py = y1 + t * dy
        let ex = x0 - px, ey = y0 - py
        return sqrt(ex * ex + ey * ey)
    }
}