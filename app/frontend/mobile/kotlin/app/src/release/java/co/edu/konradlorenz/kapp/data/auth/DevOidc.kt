package co.edu.konradlorenz.kapp.data.auth

import android.content.Context

/**
 * The release side of src/debug's DevOidc: a release build never signs in through the fake OIDC
 * server, whatever the Gradle properties say.
 */
internal object DevOidc {
    fun signIn(context: Context): MicrosoftSignIn? = null
}
