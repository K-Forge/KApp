package co.edu.konradlorenz.kapp.data.auth

import android.app.Activity
import android.content.Context
import co.edu.konradlorenz.kapp.BuildConfig
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Step 1 of the sign-in in issue #46: Microsoft's own sign-in, which ends in an ID token for
 * `POST /auth/microsoft`. What KApp does with that token is SessionManager's business.
 */
interface MicrosoftSignIn {

    /**
     * `true` when no Microsoft tenant is configured and this is a debug build: [idToken] then
     * answers a made-up token, which the mocks accept and a real server never will.
     */
    val isFake: Boolean

    /**
     * Runs Microsoft's sign-in from [activity] and returns the ID token.
     *
     * Throws [SignInCancelledException] when the person backs out, [MicrosoftUnreachableException]
     * when there is no network, [SignInNotConfiguredException] in a release build with no tenant,
     * and [MicrosoftSignInException] for anything Microsoft itself refused.
     */
    suspend fun idToken(activity: Activity): String

    /** Forgets the Microsoft account, so the next sign-in asks which one. Never fails. */
    suspend fun signOut()

    companion object {
        /**
         * MSAL when the build has a client id, the fake in a debug build without one, and a sign-in
         * that explains itself in a release build without one. See "Microsoft sign-in" in the
         * module README for where the client id comes from.
         */
        fun forThisBuild(context: Context): MicrosoftSignIn = when {
            BuildConfig.MSAL_CLIENT_ID.isNotBlank() -> MsalSignIn(
                context = context.applicationContext,
                clientId = BuildConfig.MSAL_CLIENT_ID,
                tenantId = BuildConfig.MSAL_TENANT_ID,
                signatureHash = BuildConfig.MSAL_SIGNATURE_HASH,
            )
            BuildConfig.DEBUG -> FakeSignIn
            else -> NotConfigured
        }
    }
}

class SignInCancelledException : Exception("The person backed out of the Microsoft sign-in")

class MicrosoftUnreachableException(cause: Throwable) : Exception(cause.message, cause)

class SignInNotConfiguredException : Exception("This build has no Microsoft tenant configured")

class MicrosoftSignInException(cause: Throwable) : Exception(cause.message, cause)

/**
 * The real thing, through MSAL in single-account mode (ADR 0003): one person per device, which is
 * what a student's phone is.
 *
 * The MSAL configuration is written from BuildConfig rather than kept in `res/raw`, so the client id
 * and tenant live in one place - Gradle properties - and no build carries a half-filled JSON file.
 */
private class MsalSignIn(
    private val context: Context,
    private val clientId: String,
    private val tenantId: String,
    private val signatureHash: String,
) : MicrosoftSignIn {

    override val isFake = false

    private val creating = Mutex()
    private var application: ISingleAccountPublicClientApplication? = null

    override suspend fun idToken(activity: Activity): String {
        val app = application()
        // Single-account mode refuses to sign in over an account it already holds. One left over
        // from an earlier session is signed out first, so the account picker always shows.
        try {
            withContext(Dispatchers.IO) {
                if (app.currentAccount?.currentAccount != null) app.signOut()
            }
        } catch (e: MsalException) {
            throw translate(e)
        }
        val result = suspendCancellableCoroutine { continuation ->
            app.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(SCOPES)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(result: IAuthenticationResult) =
                            continuation.resume(result)

                        override fun onError(exception: MsalException) =
                            continuation.resumeWithException(translate(exception))

                        override fun onCancel() =
                            continuation.resumeWithException(SignInCancelledException())
                    })
                    .build(),
            )
        }
        return result.account.idToken
            ?: throw MicrosoftSignInException(IllegalStateException("Microsoft returned no ID token"))
    }

    override suspend fun signOut() {
        try {
            val app = application()
            withContext(Dispatchers.IO) { app.signOut() }
        } catch (_: Exception) {
            // Nothing to sign out of, or MSAL could not start: either way there is no account left.
        }
    }

    private suspend fun application(): ISingleAccountPublicClientApplication =
        creating.withLock {
            application ?: create().also { application = it }
        }

    private suspend fun create(): ISingleAccountPublicClientApplication {
        val config = withContext(Dispatchers.IO) {
            File(context.noBackupFilesDir, "msal_config.json").apply { writeText(configJson()) }
        }
        return suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) =
                        continuation.resume(application)

                    override fun onError(exception: MsalException) =
                        continuation.resumeWithException(translate(exception))
                },
            )
        }
    }

    /**
     * MSAL's configuration file, for the university tenant only (`AzureADMyOrg`): a personal
     * Microsoft account or another organisation's is refused by Microsoft before KApp sees it.
     *
     * The redirect URI is the one the app registration lists for Android, built from the package
     * and the signing certificate's hash - which is why each signing key needs its own entry in the
     * registration, and why the manifest declares the same path for BrowserTabActivity.
     */
    private fun configJson(): String = buildJsonObject {
        put("client_id", clientId)
        put("authorization_user_agent", "DEFAULT")
        put(
            "redirect_uri",
            "msauth://${context.packageName}/${URLEncoder.encode(signatureHash, "UTF-8")}",
        )
        put("account_mode", "SINGLE")
        put("broker_redirect_uri_registered", false)
        putJsonArray("authorities") {
            addJsonObject {
                put("type", "AAD")
                putJsonObject("audience") {
                    put("type", "AzureADMyOrg")
                    put("tenant_id", tenantId)
                }
            }
        }
    }.toString()

    private fun translate(exception: MsalException): Exception = when {
        exception is MsalClientException && exception.errorCode in NETWORK_ERRORS ->
            MicrosoftUnreachableException(exception)
        else -> MicrosoftSignInException(exception)
    }

    private companion object {
        /**
         * MSAL adds `openid`, `profile` and `offline_access` by itself and refuses them if asked
         * for. `User.Read` is the one scope ADR 0003 adds to those.
         */
        val SCOPES = listOf("User.Read")

        val NETWORK_ERRORS = setOf(
            MsalClientException.DEVICE_NETWORK_NOT_AVAILABLE,
            MsalClientException.IO_ERROR,
        )
    }
}

/**
 * Debug builds with no tenant configured. The mocks accept any ID token of 20 characters or more,
 * so the screens can be built before the university's tenant exists (issue #46).
 */
private object FakeSignIn : MicrosoftSignIn {
    override val isFake = true
    override suspend fun idToken(activity: Activity) = "debug-build-fake-microsoft-id-token"
    override suspend fun signOut() = Unit
}

/** A release build with no tenant. Says so, rather than sending a made-up token to a real server. */
private object NotConfigured : MicrosoftSignIn {
    override val isFake = false
    override suspend fun idToken(activity: Activity): String = throw SignInNotConfiguredException()
    override suspend fun signOut() = Unit
}
