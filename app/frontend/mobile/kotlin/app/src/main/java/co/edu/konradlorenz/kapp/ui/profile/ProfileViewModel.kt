package co.edu.konradlorenz.kapp.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.profile.ProfileRepository
import co.edu.konradlorenz.kapp.data.profile.ProfileState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Perfil: the profile [repository] already holds, the one edit user 1.0.0 allows - the picture -
 * and signing out.
 */
class ProfileViewModel(
    private val repository: ProfileRepository,
    private val signOutOfEverything: suspend () -> Unit,
) : ViewModel() {

    val profile: StateFlow<ProfileState> = repository.state

    /** True while the picture is being removed, so the button cannot fire twice. */
    var removingAvatar by mutableStateOf(false)
        private set

    /** The last removal failed. Cleared by the next attempt. */
    var avatarError by mutableStateOf(false)
        private set

    var signingOut by mutableStateOf(false)
        private set

    fun retry() {
        viewModelScope.launch { repository.load() }
    }

    fun removeAvatar() {
        if (removingAvatar) return
        removingAvatar = true
        avatarError = false
        viewModelScope.launch {
            avatarError = !repository.removeAvatar()
            removingAvatar = false
        }
    }

    /** The navigation takes the person to the login once the session is gone. */
    fun signOut() {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch { signOutOfEverything() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KAppApplication).container
                ProfileViewModel(container.profile, container::signOut)
            }
        }
    }
}
