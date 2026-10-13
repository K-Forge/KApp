package co.edu.konradlorenz.kapp.ui.login

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.auth.MicrosoftSignIn
import co.edu.konradlorenz.kapp.data.auth.MicrosoftUnreachableException
import co.edu.konradlorenz.kapp.data.auth.SignInCancelledException
import co.edu.konradlorenz.kapp.data.auth.SignInNotConfiguredException
import co.edu.konradlorenz.kapp.data.profile.MockProfilePreference
import co.edu.konradlorenz.kapp.data.profile.ProfileRepository
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** What the sign-in can end in, short of getting in. Each one has its own message. */
enum class SignInError {
    /** `401` from `/auth/microsoft`: KApp did not accept the ID token. */
    Rejected,

    /** `403`: the account exists and is deactivated. */
    Deactivated,

    /** Nothing answered: no network, or the server is down. */
    Offline,

    /** A release build with no Microsoft tenant configured. */
    NotConfigured,

    /** Microsoft refused, or KApp answered something it should not have. */
    Failed,
}

/**
 * The profile a debug build asks the mocks for: one of the named examples of `GET /api/users/me`
 * in docs/api/user.openapi.yaml. It is how the tabs of a professor or of staff can be seen without
 * an account of each kind (issue #46, "Done when").
 */
enum class MockProfile(val example: String) {
    Student("student"),
    Professor("professor"),
    StaffAdmin("staffAdmin"),
}

/**
 * The sign-in of issue #46: Microsoft's sign-in, its ID token to `POST /auth/microsoft`, then the
 * profile, whose roles decide the tabs (ProfileRepository).
 *
 * There are no fields to hold: the address and the password are typed into Microsoft's page, never
 * into KApp's (auth.openapi.yaml: "It stores no password for a member of the university").
 */
class LoginViewModel(
    private val microsoft: MicrosoftSignIn,
    private val session: SessionManager,
    private val profile: ProfileRepository,
    private val mockProfiles: MockProfilePreference,
) : ViewModel() {

    var signingIn by mutableStateOf(false)
        private set

    /** The last attempt's failure, until the next one starts. */
    var error by mutableStateOf<SignInError?>(null)
        private set

    /** Only offered when the sign-in is the debug build's fake, which is when the mocks answer. */
    val offersMockProfiles: Boolean
        get() = microsoft.isFake

    var mockProfile by mutableStateOf(
        MockProfile.entries.firstOrNull { it.example == mockProfiles.example } ?: MockProfile.Student,
    )
        private set

    fun onMockProfileChange(profile: MockProfile) {
        mockProfile = profile
        mockProfiles.example = profile.example
    }

    fun signIn(activity: Activity, onSignedIn: () -> Unit) {
        if (signingIn) return
        signingIn = true
        error = null
        viewModelScope.launch {
            try {
                session.signIn(microsoft.idToken(activity))
                // Waited for so the first screen already has the right tabs. If it fails the
                // token's roles stand in, and Perfil offers to try again: refusing entry over a
                // profile call would be worse than showing the tabs the token says.
                profile.load()
                onSignedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SignInCancelledException) {
                // Backing out of Microsoft's page is a choice, not a failure: nothing to show.
            } catch (e: Exception) {
                error = classify(e)
            } finally {
                signingIn = false
            }
        }
    }

    private fun classify(e: Exception): SignInError = when (e) {
        is HttpException -> when (e.code()) {
            401 -> SignInError.Rejected
            403 -> SignInError.Deactivated
            else -> SignInError.Failed
        }
        is IOException, is MicrosoftUnreachableException -> SignInError.Offline
        is SignInNotConfiguredException -> SignInError.NotConfigured
        else -> SignInError.Failed
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KAppApplication).container
                LoginViewModel(
                    container.microsoft,
                    container.session,
                    container.profile,
                    container.mockProfile,
                )
            }
        }
    }
}
