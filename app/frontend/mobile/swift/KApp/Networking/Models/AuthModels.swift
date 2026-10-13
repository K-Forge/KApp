import Foundation

// Shapes from docs/api/auth.openapi.yaml and docs/api/user.openapi.yaml.

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

/// `TokenResponse`. No refresh token is issued.
struct TokenResponse: Decodable, Equatable {
    let accessToken: String
    let tokenType: String
    let expiresIn: Int
    let userId: String
    let roles: [String]
}

struct ResendVerificationRequest: Encodable {
    let email: String
}

/// `UserProfile`, the parts of it the app reads.
struct UserProfile: Decodable, Equatable {
    let id: String
    let email: String
    let firstName: String
    let lastName: String
    let role: String
    let academic: AcademicRecord?

    /// Null for a guest. Everything a timetable is created with comes from here.
    struct AcademicRecord: Decodable, Equatable {
        let studentCode: String
        let programCode: String
        let pensumCode: String
        let currentLevel: Int
    }

    var fullName: String { "\(firstName) \(lastName)" }
}
