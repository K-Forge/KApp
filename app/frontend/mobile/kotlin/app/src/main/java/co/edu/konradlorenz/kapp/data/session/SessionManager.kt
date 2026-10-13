package co.edu.konradlorenz.kapp.data.session

import co.edu.konradlorenz.kapp.data.network.AuthApi
import co.edu.konradlorenz.kapp.data.network.MicrosoftSignInRequest
import co.edu.konradlorenz.kapp.data.network.RefreshRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the session: signs in with a Microsoft ID token, hands out an access token that is still
 * good, renews it, and signs out. The rules are those of "Keeping the session" in issue #46 and of
 * `/auth/refresh` in docs/api/auth.openapi.yaml.
 *
 * **One renewal at a time.** The refresh token rotates, and presenting one that was already used
 * revokes its whole family - every device signed in to the account is thrown out. Two requests
 * renewing at once would do exactly that, so every read and write of the session goes through
 * [mutex], and a request that waited behind a renewal uses the token it produced instead of
 * renewing again.
 *
 * [auth] must not renew by itself: it is the public client, with no `Authorization` header.
 */
class SessionManager(
    private val auth: AuthApi,
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private val current = MutableStateFlow(loadSaved())

    /** The session, or `null` when nobody is signed in. Turns `null` when the session ends. */
    val session: StateFlow<Session?> = current.asStateFlow()

    /**
     * Exchanges Microsoft's [idToken] for KApp's tokens and keeps them.
     *
     * Throws what the call throws: an [HttpException] with `401` (the ID token is not valid) or
     * `403` (the account is deactivated), or an [IOException] when nothing answered.
     */
    suspend fun signIn(idToken: String): Session {
        val tokens = auth.signInWithMicrosoft(MicrosoftSignInRequest(idToken = idToken))
        return mutex.withLock { save(tokens.toSession(now())) }
    }

    /** Records what `GET /api/users/me` said the roles are. They decide the tabs. */
    suspend fun setProfileRoles(roles: List<String>) {
        mutex.withLock {
            current.value?.let { save(it.copy(profileRoles = roles)) }
        }
    }

    /**
     * An access token to send, renewed first if it has less than [RENEW_MARGIN_MS] to live.
     *
     * `null` only when there is no session, or renewing it ended it. When the server could not be
     * reached the old token comes back as it is: it may still work, and if it does not the `401`
     * goes through [renewAfterRejection].
     */
    suspend fun accessToken(): String? = mutex.withLock {
        val session = current.value ?: return@withLock null
        if (now() < session.accessExpiresAt - RENEW_MARGIN_MS) return@withLock session.accessToken
        when (val renewal = renewLocked(session)) {
            is Renewal.Renewed -> renewal.session.accessToken
            Renewal.Ended -> null
            Renewal.Unreachable -> session.accessToken
        }
    }

    /**
     * The server refused [rejected] with `401`. Returns the token to retry with, or `null` to give
     * up and let the `401` through.
     *
     * If the session already holds a different token, another request renewed while this one was
     * in flight: that token is the answer, and renewing again would spend a refresh token for
     * nothing.
     */
    suspend fun renewAfterRejection(rejected: String): String? = mutex.withLock {
        val session = current.value ?: return@withLock null
        if (session.accessToken != rejected) return@withLock session.accessToken
        (renewLocked(session) as? Renewal.Renewed)?.session?.accessToken
    }

    /**
     * Signs out: revokes the refresh token on the server, then forgets both tokens.
     *
     * Never fails. `/auth/logout` always answers `204`, and when it cannot be reached the tokens are
     * forgotten anyway - the refresh token then expires on its own, unused, within 30 days.
     */
    suspend fun signOut() {
        mutex.withLock {
            val refreshToken = current.value?.refreshToken
            if (refreshToken != null) {
                try {
                    auth.logout(RefreshRequest(refreshToken))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Signing out locally is what the person asked for; the server is a courtesy.
                }
            }
            end()
        }
    }

    /** Must be called holding [mutex]. */
    private suspend fun renewLocked(session: Session): Renewal {
        val refreshToken = session.refreshToken
        if (refreshToken == null) {
            // A visitor pass does not renew. Once its token is refused, it is over.
            end()
            return Renewal.Ended
        }
        return try {
            val tokens = auth.refresh(RefreshRequest(refreshToken))
            // Both tokens are replaced: the refresh token just sent no longer works.
            Renewal.Renewed(save(tokens.toSession(now(), session.profileRoles)))
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                // Unknown, expired, revoked, reused or deactivated: sign in again. Never retry with
                // the same refresh token - if it was reused, its family is already gone.
                end()
                Renewal.Ended
            } else {
                Renewal.Unreachable
            }
        } catch (_: IOException) {
            Renewal.Unreachable
        }
    }

    private fun save(session: Session): Session {
        store.write(session)
        current.value = session
        return session
    }

    private fun end() {
        store.clear()
        current.value = null
    }

    /** The saved session if it can still be used. One that cannot is deleted on the way. */
    private fun loadSaved(): Session? {
        val saved = store.read() ?: return null
        if (saved.isUsableAt(now())) return saved
        store.clear()
        return null
    }

    private sealed interface Renewal {
        data class Renewed(val session: Session) : Renewal
        data object Ended : Renewal
        data object Unreachable : Renewal
    }

    companion object {
        /**
         * How early before expiry an access token is renewed. "Shortly before", in the contract's
         * words: early enough that a request sent on a slow network does not arrive expired.
         */
        const val RENEW_MARGIN_MS = 2 * 60 * 1000L
    }
}
