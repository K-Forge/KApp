package co.edu.konradlorenz.kapp.data.profile

import co.edu.konradlorenz.kapp.data.cache.OfflineCache
import co.edu.konradlorenz.kapp.data.network.UserApi
import co.edu.konradlorenz.kapp.data.network.UserProfile
import co.edu.konradlorenz.kapp.data.network.UserProfileUpdate
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Where the signed-in person's profile is. */
sealed interface ProfileState {
    /** Not asked for yet, or on its way. */
    data object Loading : ProfileState

    /**
     * [offlineSince] is set when there was no network and this is the copy saved at that moment
     * (OfflineCache), rather than the server's answer just now.
     */
    data class Ready(val profile: UserProfile, val offlineSince: Long? = null) : ProfileState

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
    private val offline: OfflineCache? = null,
) {
    private val loading = Mutex()
    private val current = MutableStateFlow<ProfileState>(ProfileState.Loading)

    val state: StateFlow<ProfileState> = current.asStateFlow()

    /**
     * Reads the profile, unless it is already here - a copy from [offline] is read again, in case
     * the network is back. Concurrent callers wait for the one call rather than making their own.
     * Never throws, short of cancellation: a failure is [ProfileState.Failed].
     */
    suspend fun load(): ProfileState = loading.withLock {
        current.value.takeIf { it is ProfileState.Ready && it.offlineSince == null }?.let { return@withLock it }
        current.value = ProfileState.Loading
        current.value = try {
            val profile = users.me(prefer())
            session.setProfileRoles(profile.roles)
            offline?.save(CACHE, UserProfile.serializer(), profile)
            ProfileState.Ready(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // No network: the last profile this phone saw, if there is one. The roles in the
            // session were saved with it, so the tabs are already right.
            offline?.read(CACHE, UserProfile.serializer())
                ?.let { ProfileState.Ready(it.value, offlineSince = it.savedAt) }
                ?: ProfileState.Failed
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
            val updated = profile.copy(avatarUrl = null)
            offline?.save(CACHE, UserProfile.serializer(), updated)
            current.value = ProfileState.Ready(updated)
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

    private companion object {
        const val CACHE = "profile"
    }
}
