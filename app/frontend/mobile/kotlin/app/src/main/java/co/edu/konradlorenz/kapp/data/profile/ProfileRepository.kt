package co.edu.konradlorenz.kapp.data.profile

import co.edu.konradlorenz.kapp.data.network.UserApi
import co.edu.konradlorenz.kapp.data.network.UserProfile
import co.edu.konradlorenz.kapp.data.network.UserProfileUpdate
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** Where the signed-in person's profile is. */
sealed interface ProfileState {
    /** Not asked for yet, or on its way. */
    data object Loading : ProfileState

    data class Ready(val profile: UserProfile) : ProfileState

    /** The last attempt failed. The screens that need it offer to try again. */
    data object Failed : ProfileState
}

/**
 * The signed-in person's profile, `GET /api/users/me` in docs/api/user.openapi.yaml, read once per
 * session and shared by every screen that shows it: Inicio greets with it, Perfil draws it.
 *
 * Reading it also settles the tabs: its `roles` go to [session], which is where the bar reads the
 * profile role. A role changed in the directory therefore reaches the tabs the next time the app
 * opens, not only at the next sign-in.
 *
 * [prefer] is the debug build's choice of mock profile (MockProfilePreference), or `null`.
 */
class ProfileRepository(
    private val users: UserApi,
    private val session: SessionManager,
    private val prefer: () -> String?,
) {
    private val loading = Mutex()
    private val current = MutableStateFlow<ProfileState>(ProfileState.Loading)

    val state: StateFlow<ProfileState> = current.asStateFlow()

    /**
     * Reads the profile, unless it is already here. Concurrent callers wait for the one call rather
     * than making their own. Never throws, short of cancellation: a failure is [ProfileState.Failed].
     */
    suspend fun load(): ProfileState = loading.withLock {
        current.value.takeIf { it is ProfileState.Ready }?.let { return@withLock it }
        current.value = ProfileState.Loading
        current.value = try {
            val profile = users.me(prefer())
            session.setProfileRoles(profile.roles)
            ProfileState.Ready(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ProfileState.Failed
        }
        current.value
    }

    /**
     * Removes the profile picture. `true` when the server took it.
     *
     * The profile kept is the one already here with the picture taken off, not the server's answer.
     * Against a real server the two are the same; against the mocks, which store nothing, the answer
     * is always the student example - and taking it would turn a professor into Pepito.
     */
    suspend fun removeAvatar(): Boolean {
        val profile = (current.value as? ProfileState.Ready)?.profile ?: return false
        return try {
            users.updateMe(UserProfileUpdate(avatarUrl = null))
            current.value = ProfileState.Ready(profile.copy(avatarUrl = null))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Forgets the profile. Called when the session ends, so the next person starts clean. */
    fun clear() {
        current.value = ProfileState.Loading
    }
}
