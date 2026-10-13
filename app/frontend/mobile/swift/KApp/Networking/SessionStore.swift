import Foundation
import Security

/// A signed-in student, as `POST /auth/login` returned them.
struct Session: Codable, Equatable {
    let accessToken: String
    let userId: String
    let roles: [String]
    /// The address they signed in with. The token carries it too, but reading it here avoids
    /// decoding a JWT the client has no key to verify anyway.
    let email: String
}

/// Where a session outlives the app.
protocol SessionStore {
    func load() -> Session?
    func save(_ session: Session)
    func clear()
}

/// The Keychain, which is where a token belongs on iOS: encrypted at rest, and not in a backup
/// that restores onto another device.
///
/// The product decision is that a student stays signed in on their phone. The contract cannot
/// honour that for long yet - `expiresIn` is an hour and there is no refresh token - so a stored
/// session is kept until the server answers `401`, and only then is the student sent back to the
/// login. The client does not guess at expiry on its own.
struct KeychainSessionStore: SessionStore {
    private let service = "co.edu.konradlorenz.kapp.session"
    private let account = "current"

    func load() -> Session? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return try? JSONDecoder().decode(Session.self, from: data)
    }

    func save(_ session: Session) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        clear()
        var query = baseQuery
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

/// Keeps nothing past the process. For previews, tests, and a sign-in with the switch off.
final class MemorySessionStore: SessionStore {
    private var session: Session?

    init(session: Session? = nil) {
        self.session = session
    }

    func load() -> Session? { session }
    func save(_ session: Session) { self.session = session }
    func clear() { session = nil }
}
