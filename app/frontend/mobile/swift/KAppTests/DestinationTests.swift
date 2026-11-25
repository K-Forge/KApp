import UIKit
import XCTest
@testable import KApp

/// The bar and the shortcuts draw from `KAppDestination`, and an SF Symbol name that does not exist
/// draws nothing without an error - the first build shipped an active Horario tab with no icon.
final class DestinationTests: XCTestCase {

    func testTheBarIsInTheOrderTheMockupDrawsWithInicioInTheMiddle() {
        XCTAssertEqual(KAppDestination.allCases, [.profile, .semaphore, .home, .map, .schedule])
    }

    func testEveryTabIconExists() {
        for destination in KAppDestination.allCases {
            XCTAssertNotNil(UIImage(systemName: destination.symbol), "\(destination).symbol")
            XCTAssertNotNil(UIImage(systemName: destination.activeSymbol), "\(destination).activeSymbol")
        }
    }
}
