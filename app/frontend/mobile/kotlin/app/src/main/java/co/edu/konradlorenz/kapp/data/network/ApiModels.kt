package co.edu.konradlorenz.kapp.data.network

import kotlinx.serialization.Serializable

// The shapes the contracts in docs/api/ define, field for field. Only what the client reads so far
// is modelled; kotlinx.serialization is told to ignore the rest (see KAppApi), so a field added to a
// contract later does not break a build that does not use it yet.

/** `HealthStatus` in auth.openapi.yaml. Answered by `GET /auth/health`, public. */
@Serializable
data class HealthStatus(val status: String)

/** `MicrosoftSignInRequest` in auth.openapi.yaml. */
@Serializable
data class MicrosoftSignInRequest(
    /** The ID token Microsoft returned. The mocks accept any string of 20 characters or more. */
    val idToken: String,
    /** `APP` for the mobile clients; `PORTAL` is the admin portal's. */
    val client: String = "APP",
)

/** `RefreshRequest` in auth.openapi.yaml. The body of both `POST /auth/refresh` and `/auth/logout`. */
@Serializable
data class RefreshRequest(val refreshToken: String)

/**
 * `TokenResponse` in auth.openapi.yaml. What `POST /auth/microsoft` and `POST /auth/refresh` answer.
 *
 * Kept by SessionManager, which is the only thing that should ever read [refreshToken].
 */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    /** `null` for a visitor pass, which does not renew. */
    val refreshToken: String? = null,
    val refreshExpiresIn: Int? = null,
    /** Not always a UUID: a visitor pass is `visitor:<pass id>`. */
    val userId: String,
    /** Profile role first, then permissions, e.g. `["ROLE_STAFF", "ROLE_ADMIN"]`. */
    val roles: List<String>,
)

/** `UserProfile` in user.openapi.yaml. What `GET /api/users/me` answers. */
@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val avatarUrl: String? = null,
    val roles: List<String>,
    val active: Boolean,
    /** Read from SINU for a student; `null` for anybody else. */
    val academic: AcademicInfo? = null,
)

/** `AcademicInfo` in user.openapi.yaml. */
@Serializable
data class AcademicInfo(
    val programCode: String,
    val programName: String,
    val pensumCode: String,
    val currentLevel: Int,
)
