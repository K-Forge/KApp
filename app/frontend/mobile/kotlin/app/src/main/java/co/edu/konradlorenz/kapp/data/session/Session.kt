package co.edu.konradlorenz.kapp.data.session

import co.edu.konradlorenz.kapp.data.network.TokenResponse
import kotlinx.serialization.Serializable

/**
 * A signed-in session, as SessionManager keeps it and SessionStore writes it to disk.
 *
 * Expiries are absolute (epoch milliseconds) rather than the `expiresIn` seconds the API sends: a
 * session read back from disk a week later has to know whether its tokens are still good without
 * knowing when they were issued.
 */
@Serializable
data class Session(
    val accessToken: String,
    val accessExpiresAt: Long,
    /** `null` for a visitor pass, which does not renew. */
    val refreshToken: String?,
    val refreshExpiresAt: Long?,
    val userId: String,
    /** The roles in the last token: what the API authorises by. */
    val tokenRoles: List<String>,
    /**
     * The roles `GET /api/users/me` answered at sign-in. Empty until it has answered.
     *
     * Kept apart from [tokenRoles] because this is what the tabs follow, and against the mocks the
     * two differ on purpose: the token is always a student's, while `Prefer: example=professor` on
     * the profile is how a debug build shows a professor's tabs (issue #46, "Done when").
     */
    val profileRoles: List<String> = emptyList(),
) {
    /** The profile role the tabs follow: the profile's if it has answered, the token's if not. */
    val profileRole: ProfileRole?
        get() = ProfileRole.of(profileRoles) ?: ProfileRole.of(tokenRoles)

    /**
     * Whether anything can still be done with this session at [now]. A session with a live refresh
     * token is usable even with its access token expired: the next call renews it.
     */
    fun isUsableAt(now: Long): Boolean = when (refreshExpiresAt) {
        null -> now < accessExpiresAt
        else -> refreshToken != null && now < refreshExpiresAt
    }
}

/** [tokens] as a session, issued at [now]. [profileRoles] carries over from the session renewed. */
fun TokenResponse.toSession(now: Long, profileRoles: List<String> = emptyList()) = Session(
    accessToken = accessToken,
    accessExpiresAt = now + expiresIn * 1000L,
    refreshToken = refreshToken,
    refreshExpiresAt = refreshExpiresIn?.let { now + it * 1000L },
    userId = userId,
    tokenRoles = roles,
    profileRoles = profileRoles,
)

/**
 * Where a session survives the app being closed. The one on a device is KeystoreSessionStore;
 * tests use a map.
 */
interface SessionStore {
    /** The saved session, or `null` when there is none or it can no longer be read. */
    fun read(): Session?

    fun write(session: Session)

    fun clear()
}
