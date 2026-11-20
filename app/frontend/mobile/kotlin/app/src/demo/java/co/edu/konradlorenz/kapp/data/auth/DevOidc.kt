package co.edu.konradlorenz.kapp.data.auth

import android.content.Context

/**
 * The demo side of src/debug's DevOidc: the demo has no server to sign in to, so it always takes
 * the made-up token and the profile picker on the login.
 */
internal object DevOidc {
    fun signIn(context: Context): MicrosoftSignIn? = null
}
