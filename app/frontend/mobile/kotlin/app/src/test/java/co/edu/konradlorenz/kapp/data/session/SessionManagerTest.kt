package co.edu.konradlorenz.kapp.data.session

import co.edu.konradlorenz.kapp.data.network.AuthApi
import co.edu.konradlorenz.kapp.data.network.HealthStatus
import co.edu.konradlorenz.kapp.data.network.MicrosoftSignInRequest
import co.edu.konradlorenz.kapp.data.network.RefreshRequest
import co.edu.konradlorenz.kapp.data.network.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The rules of "Keeping the session" in issue #46, each against a fake server. The clock is a
 * variable, so "an hour later" is an assignment rather than a wait.
 */
class SessionManagerTest {

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val server = FakeAuth()

    private fun manager() = SessionManager(server, store, now = { now })

    @Test
    fun `signing in keeps both tokens and their expiries`() = runBlocking {
        val session = manager().signIn(ID_TOKEN)

        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(now + 3_600_000, session.accessExpiresAt)
        assertEquals(now + 2_592_000_000, session.refreshExpiresAt)
        assertEquals(session, store.saved)
        assertEquals(listOf(MicrosoftSignInRequest(ID_TOKEN, "APP")), server.signIns)
    }

    @Test
    fun `a token with time left is used as it is`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        now += 30 * 60_000

        assertEquals("access-1", manager.accessToken())
        assertEquals(emptyList<String>(), server.refreshes)
    }

    @Test
    fun `shortly before expiry it renews, and replaces both tokens`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        now += 59 * 60_000

        assertEquals("access-2", manager.accessToken())
        assertEquals(listOf("refresh-1"), server.refreshes)
        assertEquals("refresh-2", store.saved?.refreshToken)
    }

    @Test
    fun `renewing keeps the roles the profile answered`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        manager.setProfileRoles(listOf("ROLE_PROFESSOR"))
        now += 2 * 60 * 60_000

        manager.accessToken()

        assertEquals(ProfileRole.Professor, manager.session.value?.profileRole)
    }

    @Test
    fun `a refused renewal ends the session`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        server.refuseRefresh = 401
        now += 2 * 60 * 60_000

        assertNull(manager.accessToken())
        assertNull(manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `a deactivated account ends the session too`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        server.refuseRefresh = 403
        now += 2 * 60 * 60_000

        assertNull(manager.accessToken())
        assertNull(manager.session.value)
    }

    @Test
    fun `with no network the session survives and the old token is tried`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        server.offline = true
        now += 2 * 60 * 60_000

        assertEquals("access-1", manager.accessToken())
        assertNotNull(manager.session.value)
    }

    @Test
    fun `a 401 renews once, and a second request rejected with the same token reuses the renewal`() =
        runBlocking {
            val manager = manager()
            manager.signIn(ID_TOKEN)

            assertEquals("access-2", manager.renewAfterRejection("access-1"))
            assertEquals("access-2", manager.renewAfterRejection("access-1"))
            assertEquals(listOf("refresh-1"), server.refreshes)
        }

    @Test
    fun `requests expiring together spend one refresh token, not one each`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        server.refreshDelayMs = 50
        now += 2 * 60 * 60_000

        val tokens = (1..8).map { async(Dispatchers.Default) { manager.accessToken() } }.awaitAll()

        assertEquals(List(8) { "access-2" }, tokens)
        // Two renewals would have presented refresh-1 twice: the server revokes the family.
        assertEquals(listOf("refresh-1"), server.refreshes)
    }

    @Test
    fun `signing out revokes the refresh token and forgets both`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)

        manager.signOut()

        assertEquals(listOf("refresh-1"), server.logouts)
        assertNull(manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `signing out with no network still signs out`() = runBlocking {
        val manager = manager()
        manager.signIn(ID_TOKEN)
        server.offline = true

        manager.signOut()

        assertNull(manager.session.value)
        assertNull(store.saved)
    }

    @Test
    fun `a saved session opens the app signed in`() = runBlocking {
        manager().signIn(ID_TOKEN)
        now += 10 * 24 * 60 * 60_000L

        assertEquals("refresh-1", manager().session.value?.refreshToken)
    }

    @Test
    fun `a session unused for more than 30 days is gone`() = runBlocking {
        manager().signIn(ID_TOKEN)
        now += 31 * 24 * 60 * 60_000L

        assertNull(manager().session.value)
        assertNull(store.saved)
    }

    private class MemoryStore : SessionStore {
        var saved: Session? = null
        override fun read() = saved
        override fun write(session: Session) {
            saved = session
        }
        override fun clear() {
            saved = null
        }
    }

    /** Issues access-N and refresh-N, and rotates like the contract says. */
    private class FakeAuth : AuthApi {
        val signIns = mutableListOf<MicrosoftSignInRequest>()
        val refreshes = mutableListOf<String>()
        val logouts = mutableListOf<String>()
        var refuseRefresh: Int? = null
        var offline = false
        var refreshDelayMs = 0L
        private var issued = 0

        override suspend fun health() = HealthStatus("UP")

        override suspend fun signInWithMicrosoft(request: MicrosoftSignInRequest): TokenResponse {
            signIns += request
            return issue()
        }

        override suspend fun refresh(request: RefreshRequest): TokenResponse {
            if (offline) throw IOException("offline")
            delay(refreshDelayMs)
            synchronized(refreshes) { refreshes += request.refreshToken }
            refuseRefresh?.let { throw HttpException(Response.error<Any>(it, "".toResponseBody())) }
            return issue()
        }

        override suspend fun logout(request: RefreshRequest): Response<Unit> {
            if (offline) throw IOException("offline")
            logouts += request.refreshToken
            return Response.success(204, Unit)
        }

        private fun issue(): TokenResponse {
            issued++
            return TokenResponse(
                accessToken = "access-$issued",
                tokenType = "Bearer",
                expiresIn = 3600,
                refreshToken = "refresh-$issued",
                refreshExpiresIn = 2_592_000,
                userId = "3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73",
                roles = listOf("ROLE_STUDENT"),
            )
        }
    }

    private companion object {
        const val ID_TOKEN = "an-id-token-from-microsoft"
    }
}
