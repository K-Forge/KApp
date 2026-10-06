package co.edu.konradlorenz.kapp.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.konradlorenz.kapp.BuildConfig
import co.edu.konradlorenz.kapp.data.network.checkMockConnection
import kotlinx.coroutines.launch

/** The only domain KApp accounts use. Institutional sign-in is the whole point of the screen. */
const val INSTITUTIONAL_DOMAIN = "@konradlorenz.edu.co"

/**
 * Builds the address the API expects out of whatever the student typed.
 *
 * The field prints the domain in grey after the cursor, so the usual input is just the local part
 * ("pepito.perez"). Pasting the whole address must not produce a doubled domain - the frozen web
 * client had the same rule, in app/frontend/web/login.html.
 *
 * Returns an empty string for an empty field rather than a bare domain.
 */
fun institutionalEmail(typed: String): String {
    val localPart = typed.trim().substringBefore('@')
    return if (localPart.isEmpty()) "" else localPart + INSTITUTIONAL_DOMAIN
}

/**
 * Holds what the login screen has on it. There is no sign-in yet, so this class cannot fail and has
 * no error state to expose.
 *
 * The one call it makes is the debug build's connection check on submit (see [signIn]). The real
 * sign-in is Microsoft's, issue #46: the submit path grows a result state here, and the screen grows
 * the cases drawn in docs/design/mobile/EstadosLogin.dc.html. Nothing above this class has to move
 * for that.
 */
class LoginViewModel : ViewModel() {

    /** What the student typed, without the domain. */
    var emailLocalPart by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var passwordVisible by mutableStateOf(false)
        private set

    /**
     * The mockup ships this switch on, so it starts on.
     *
     * It only holds interface state today. There is no token to keep without a backend, and the
     * contract could not keep one for long anyway: it has no refresh token and expiresIn is an
     * hour for everybody. See "Pendientes de backend" in docs/design/mobile/README.md.
     */
    var keepSignedIn by mutableStateOf(true)
        private set

    /** True while the debug build's connection check runs, so the button cannot fire twice. */
    var checkingConnection by mutableStateOf(false)
        private set

    /** The address POST /auth/login will receive once there is a network layer to send it. */
    val email: String
        get() = institutionalEmail(emailLocalPart)

    /**
     * Both fields filled. This is the only validation the screen does: the server is the one that
     * knows whether the credentials are good, and inventing a client-side verdict it cannot back
     * would be a message we would have to take away later.
     */
    val canSubmit: Boolean
        get() = emailLocalPart.isNotBlank() && password.isNotBlank()

    /**
     * Lets the student in. The fields are not sent anywhere yet.
     *
     * A debug build first walks health, `POST /auth/microsoft` and `GET /api/users/me` against
     * BuildConfig.API_BASE_URL and logs the outcome under the tag `KApp.api`, then lets the student
     * in whatever it was, so a developer without the mocks running is not locked out. A release
     * build calls nothing: there is no server to call.
     */
    fun signIn(onSignedIn: () -> Unit) {
        if (!BuildConfig.DEBUG) {
            onSignedIn()
            return
        }
        if (checkingConnection) return
        checkingConnection = true
        viewModelScope.launch {
            Log.i(API_LOG_TAG, checkMockConnection())
            checkingConnection = false
            onSignedIn()
        }
    }

    fun onEmailChange(value: String) {
        // The domain is painted after the field and cannot be typed into it. Dropping everything
        // from the "@" on means a pasted full address lands correctly instead of showing twice.
        emailLocalPart = value.substringBefore('@').trim()
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun onPasswordVisibilityToggle() {
        passwordVisible = !passwordVisible
    }

    fun onKeepSignedInChange(value: Boolean) {
        keepSignedIn = value
    }
}

private const val API_LOG_TAG = "KApp.api"
