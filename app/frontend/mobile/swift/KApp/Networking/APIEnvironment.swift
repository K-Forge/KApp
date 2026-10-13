import Foundation

/// The four services the app talks to. Map-service joins when the map screen is built.
enum APIService: CaseIterable {
    case auth, user, semaphore, schedule
}

/// Where the app sends its requests.
///
/// Two targets, because the backend is reached two ways during development:
///
/// - **mock** - the Prism mocks from `docker compose --profile mock up -d`. No JVM, no database, no
///   secrets, and every contract answers with its own examples. Each service is on its own port,
///   which is the one thing that differs from production.
/// - **gateway** - the real stack behind the API gateway, one base URL for everything.
///
/// The iOS Simulator shares the Mac's network, so `localhost` is the Mac. A physical phone is not,
/// and needs the Mac's LAN address passed as `-KAppGatewayURL`.
struct APIEnvironment: Equatable {
    let name: String
    private let urls: [APIService: URL]

    func baseURL(for service: APIService) -> URL {
        // Every case is filled by both factories below, so this cannot miss.
        urls[service]!
    }

    /// Ports as `docker-compose.yml` publishes them: 4010 auth, 4011 user, 4012 semaphore,
    /// 4013 schedule.
    static let mock = APIEnvironment(
        name: "mock",
        urls: [
            .auth: URL(string: "http://localhost:4010")!,
            .user: URL(string: "http://localhost:4011")!,
            .semaphore: URL(string: "http://localhost:4012")!,
            .schedule: URL(string: "http://localhost:4013")!
        ]
    )

    static func gateway(_ base: URL) -> APIEnvironment {
        APIEnvironment(
            name: "gateway",
            urls: Dictionary(uniqueKeysWithValues: APIService.allCases.map { ($0, base) })
        )
    }

    static let defaultGateway = URL(string: "http://localhost:8080")!

    /// Chosen at launch, so switching needs no rebuild.
    ///
    /// Xcode scheme arguments land in `UserDefaults`: `-KAppAPI gateway`, optionally with
    /// `-KAppGatewayURL http://192.168.1.20:8080`. Anything else, or nothing, is the mocks - the
    /// setup that works on any machine with Docker.
    static func fromLaunchConfiguration(defaults: UserDefaults = .standard) -> APIEnvironment {
        guard defaults.string(forKey: "KAppAPI")?.lowercased() == "gateway" else { return .mock }
        let base = defaults.string(forKey: "KAppGatewayURL").flatMap(URL.init(string:)) ?? defaultGateway
        return .gateway(base)
    }
}
