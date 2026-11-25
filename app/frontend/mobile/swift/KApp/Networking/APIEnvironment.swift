import Foundation

/// Where the app sends its requests: one base URL with the gateway's routes.
///
/// Two targets during development, both on the Mac:
///
/// - **mock**, `http://localhost:4000`: the Prism mocks of every contract behind one gateway
///   (`app/backend/microservices/mock/README.md`). No JVM, no database, no account, and every
///   contract answers with its own examples. The default.
/// - **local**, `http://localhost:8080`: the real gateway of the local stack.
///
/// The iOS Simulator shares the Mac's network, so `localhost` is the Mac. A physical phone is not,
/// and needs the Mac's LAN address passed as `-KAppBaseURL`.
struct APIEnvironment: Equatable {
    let name: String
    let baseURL: URL

    static let mock = APIEnvironment(name: "mock", baseURL: URL(string: "http://localhost:4000")!)
    static let local = APIEnvironment(name: "local", baseURL: URL(string: "http://localhost:8080")!)

    /// Whether the answers are the contracts' examples. Only the mocks let a client pick one with
    /// a `Prefer` header, which is how a debug build shows another profile's screens.
    var isMock: Bool { name == Self.mock.name }

    /// Chosen at launch, so switching needs no rebuild.
    ///
    /// Xcode scheme arguments land in `UserDefaults`: `-KAppAPI local` for the local stack, and
    /// `-KAppBaseURL http://192.168.1.20:4000` to point either one somewhere else. Anything else,
    /// or nothing, is the mocks.
    static func fromLaunchConfiguration(defaults: UserDefaults = .standard) -> APIEnvironment {
        let chosen: APIEnvironment = defaults.string(forKey: "KAppAPI")?.lowercased() == local.name ? .local : .mock
        guard let override = defaults.string(forKey: "KAppBaseURL").flatMap(URL.init(string:)) else {
            return chosen
        }
        return APIEnvironment(name: chosen.name, baseURL: override)
    }
}
