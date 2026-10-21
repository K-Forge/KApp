package co.edu.konradlorenz.kapp.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.profile.LocalAvatar
import co.edu.konradlorenz.kapp.data.profile.ProfileRepository
import co.edu.konradlorenz.kapp.data.profile.ProfileState
import co.edu.konradlorenz.kapp.data.profile.cropPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Perfil: the profile [repository] already holds, the one edit user 1.0.0 allows - the picture -
 * and signing out.
 */
class ProfileViewModel(
    private val repository: ProfileRepository,
    private val signOutOfEverything: suspend () -> Unit,
    private val localAvatar: LocalAvatar? = null,
    private val decodePicture: (Uri) -> Bitmap? = { null },
) : ViewModel() {

    val profile: StateFlow<ProfileState> = repository.state

    /** The photo picked on this phone for the signed-in account, if there is one (issue #178). */
    var localPhoto by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The whole picture being adjusted, while Ajustar is open. */
    var adjusting by mutableStateOf<Bitmap?>(null)
        private set

    /** The last picked picture could not be read. Cleared by the next attempt. */
    var pickFailed by mutableStateOf(false)
        private set

    private val userId: String?
        get() = (profile.value as? ProfileState.Ready)?.profile?.id

    init {
        // Whoever is signed in, their own photo: read again whenever the profile changes.
        viewModelScope.launch {
            profile.collect { state ->
                val id = (state as? ProfileState.Ready)?.profile?.id
                localPhoto = id?.let { withContext(Dispatchers.IO) { localAvatar?.photo(it) }?.asImageBitmap() }
            }
        }
    }

    /** A picture came back from the photo picker: Ajustar opens on it. */
    fun onPicked(uri: Uri) {
        pickFailed = false
        viewModelScope.launch {
            val picture = withContext(Dispatchers.IO) { decodePicture(uri) }
            if (picture == null) pickFailed = true else adjusting = picture
        }
    }

    /** Ajustar again, from the whole picture the current photo was cropped from. */
    fun adjust() {
        val id = userId ?: return
        viewModelScope.launch {
            adjusting = withContext(Dispatchers.IO) { localAvatar?.original(id) }
        }
    }

    fun cancelAdjust() {
        adjusting = null
    }

    /** Ajustar's Guardar: crops the picture as framed and keeps it on this phone. */
    fun saveCrop(crop: AvatarCrop) {
        val id = userId ?: return
        val original = adjusting ?: return
        adjusting = null
        viewModelScope.launch {
            val photo = withContext(Dispatchers.Default) { cropPhoto(original, crop.sourceSquare()) }
            withContext(Dispatchers.IO) { localAvatar?.save(id, photo, original) }
            localPhoto = photo.asImageBitmap()
        }
    }

    /** Quitar foto: the one on this phone, and the server's when there is one. */
    fun removePhoto() {
        val id = userId ?: return
        localAvatar?.remove(id)
        localPhoto = null
        if ((profile.value as? ProfileState.Ready)?.profile?.avatarUrl != null) removeAvatar()
    }

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
                ProfileViewModel(container.profile, container::signOut, container.localAvatar, container.decodePicture)
            }
        }
    }
}
