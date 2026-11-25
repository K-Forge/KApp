import SwiftUI
import XCTest
@testable import KApp

/// The pieces every screen stands on: where requests go, how a failure reads, and the colours the
/// server sends.
final class FoundationTests: XCTestCase {

    private func defaults(_ values: [String: String]) -> UserDefaults {
        let suite = "FoundationTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        values.forEach { defaults.set($1, forKey: $0) }
        return defaults
    }

    func testWithNoArgumentsTheAppTalksToTheMocksOnOnePort() {
        let environment = APIEnvironment.fromLaunchConfiguration(defaults: defaults([:]))
        XCTAssertEqual(environment, .mock)
        XCTAssertEqual(environment.baseURL.absoluteString, "http://localhost:4000")
        XCTAssertTrue(environment.isMock)
    }

    func testLocalPointsAtTheGatewayOfTheLocalStack() {
        let environment = APIEnvironment.fromLaunchConfiguration(defaults: defaults(["KAppAPI": "Local"]))
        XCTAssertEqual(environment, .local)
        XCTAssertFalse(environment.isMock)
    }

    func testABaseURLOverrideKeepsTheChosenTarget() {
        let environment = APIEnvironment.fromLaunchConfiguration(
            defaults: defaults(["KAppAPI": "local", "KAppBaseURL": "http://192.168.1.20:8080"])
        )
        XCTAssertEqual(environment.name, "local")
        XCTAssertEqual(environment.baseURL.absoluteString, "http://192.168.1.20:8080")
    }

    func testStatusesMapToTheirCasesAndCarryTheServersMessage() {
        let body = Data(#"{"status":400,"error":"Bad Request","message":"plannedLevel: no"}"#.utf8)
        XCTAssertEqual(APIError.from(status: 400, body: body), .badRequest("plannedLevel: no"))
        XCTAssertEqual(APIError.from(status: 401, body: Data()), .unauthorized)
        XCTAssertEqual(APIError.from(status: 403, body: Data()), .forbidden)
        XCTAssertEqual(APIError.from(status: 404, body: Data()), .notFound)
        XCTAssertEqual(APIError.from(status: 409, body: Data()), .conflict(nil))
        XCTAssertEqual(APIError.from(status: 503, body: Data()), .server(status: 503))
    }

    func testAColourFromTheServerIsReadWithOrWithoutItsHash() {
        XCTAssertEqual(Color.hexValue("#539392"), 0x539392)
        XCTAssertEqual(Color.hexValue(" 3E823E "), 0x3E823E)
    }

    func testAColourThatCannotBeReadIsNil() {
        XCTAssertNil(Color.hexValue(nil))
        XCTAssertNil(Color.hexValue("#FFF"))
        XCTAssertNil(Color.hexValue("verde!"))
    }
}
