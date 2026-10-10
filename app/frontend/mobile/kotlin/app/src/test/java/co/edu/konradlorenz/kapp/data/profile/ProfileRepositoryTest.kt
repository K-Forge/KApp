package co.edu.konradlorenz.kapp.data.profile

import co.edu.konradlorenz.kapp.data.network.AuthApi
import co.edu.konradlorenz.kapp.data.network.HealthStatus
import co.edu.konradlorenz.kapp.data.network.MicrosoftSignInRequest
import co.edu.konradlorenz.kapp.data.network.RefreshRequest
import co.edu.konradlorenz.kapp.data.network.TokenResponse
import co.edu.konradlorenz.kapp.data.network.UserApi
import co.edu.konradlorenz.kapp.data.network.UserProfile
import co.edu.konradlorenz.kapp.data.network.UserProfileUpdate
import co.edu.konradlorenz.kapp.data.session.ProfileRole
import co.edu.konradlorenz.kapp.data.session.Session
import co.edu.konradlorenz.kapp.data.session.SessionManager
import co.edu.konradlorenz.kapp.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** How the profile is read once, shared, and how it settles the tabs. */
class ProfileRepositoryTest {

    private val professor = UserProfile(
        id = "6b2d9e40-1c3f-4a57-8e69-0d1f2a3b4c5d",
        email = "laura.gomez@konradlorenz.edu.co",
        firstName = "Laura Marcela",
        lastName = "Gómez Restrepo",
        avatarUrl = null,
        roles = listOf("ROLE_PROFESSOR"),
        active = true,
        academic = null,
    )

    private val users = FakeUsers()
    private val session = SessionManager(SignedInAuth, MemoryStore())
    private var prefer: String? = null
    private val repository = ProfileRepository(users, session, prefer = { prefer })

    @Test
    fun `reads the profile and hands its roles to the session`() = runBlocking {
        session.signIn("an-id-token-from-microsoft")

        val state = repository.load()

        assertEquals(ProfileState.Ready(professor), state)
        assertEquals(ProfileRole.Professor, session.session.value?.profileRole)
    }

    @Test
    fun `asks the mocks for the chosen example`() = runBlocking {
        prefer = "example=professor"
        repository.load()
        assertEquals(listOf("example=professor"), users.prefers)
    }

    @Test
    fun `a profile already here is not asked for again`() = runBlocking {
        repository.load()
        repository.load()
        assertEquals(1, users.prefers.size)
    }

    @Test
    fun `a failure is a state, and trying again reads it`() = runBlocking {
        users.offline = true
        assertEquals(ProfileState.Failed, repository.load())

        users.offline = false
        assertTrue(repository.load() is ProfileState.Ready)
    }

    @Test
    fun `removing the picture keeps the profile and drops only the picture`() = runBlocking {
        users.answer = professor.copy(avatarUrl = "https://cdn.kapp.konradlorenz.edu.co/a.jpg")
        repository.load()

        assertTrue(repository.removeAvatar())

        assertEquals(listOf(UserProfileUpdate(avatarUrl = null)), users.updates)
        assertEquals(ProfileState.Ready(professor), repository.state.value)
    }

    @Test
    fun `a removal the server refused leaves the picture`() = runBlocking {
        val withPicture = professor.copy(avatarUrl = "https://cdn.kapp.konradlorenz.edu.co/a.jpg")
        users.answer = withPicture
        repository.load()
        users.offline = true

        assertFalse(repository.removeAvatar())
        assertEquals(ProfileState.Ready(withPicture), repository.state.value)
    }

    @Test
    fun `clearing forgets the profile`() = runBlocking {
        repository.load()
        repository.clear()
        assertEquals(ProfileState.Loading, repository.state.value)
    }

    private inner class FakeUsers : UserApi {
        var answer = professor
        var offline = false
        val prefers = mutableListOf<String?>()
        val updates = mutableListOf<UserProfileUpdate>()

        override suspend fun me(prefer: String?): UserProfile {
            if (offline) throw IOException("offline")
            prefers += prefer
            return answer
        }

        override suspend fun updateMe(update: UserProfileUpdate): UserProfile {
            if (offline) throw IOException("offline")
            updates += update
            // What the mocks do: answer the student example whatever was sent.
            return answer.copy(firstName = "Pepito", avatarUrl = "https://cdn.example/x.jpg")
        }
    }

    private object SignedInAuth : AuthApi {
        override suspend fun health() = HealthStatus("UP")

        override suspend fun signInWithMicrosoft(request: MicrosoftSignInRequest) = TokenResponse(
            accessToken = "access",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "refresh-token-1234",
            refreshExpiresIn = 2_592_000,
            userId = "6b2d9e40-1c3f-4a57-8e69-0d1f2a3b4c5d",
            roles = listOf("ROLE_STUDENT"),
        )

        override suspend fun refresh(request: RefreshRequest) =
            signInWithMicrosoft(MicrosoftSignInRequest("unused"))

        override suspend fun logout(request: RefreshRequest): Response<Unit> =
            Response.success(204, Unit)
    }

    private class MemoryStore : SessionStore {
        private var saved: Session? = null
        override fun read() = saved
        override fun write(session: Session) {
            saved = session
        }
        override fun clear() {
            saved = null
        }
    }
}
