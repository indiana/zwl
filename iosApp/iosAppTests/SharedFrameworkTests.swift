import XCTest
import shared

final class SharedFrameworkTests: XCTestCase {

    func testMapStyleConstantsAvailable() {
        XCTAssertGreaterThan(MapStyle.shared.DEFAULT_LAT, 0)
        XCTAssertGreaterThan(MapStyle.shared.DEFAULT_LNG, 0)
        XCTAssertGreaterThan(MapStyle.shared.DEFAULT_ZOOM, 1)
        XCTAssertGreaterThan(MapStyle.shared.MIN_ZOOM, 0)
        XCTAssertGreaterThan(MapStyle.shared.MAX_ZOOM, MapStyle.shared.MIN_ZOOM)
    }

    func testOsmStyleJsonIsValidRasterStyle() {
        let json = MapStyle.shared.OSM_STYLE_JSON
        XCTAssertFalse(json.isEmpty)
        XCTAssertTrue(json.contains("\"type\": \"raster\""))
        XCTAssertTrue(json.contains("tile.openstreetmap.org"))
    }

    func testForestAppTypeIsExported() {
        // Smoke test: the Kotlin facade type is reachable from Swift.
        let typeName = String(describing: ForestApp.self)
        XCTAssertFalse(typeName.isEmpty)
    }
}