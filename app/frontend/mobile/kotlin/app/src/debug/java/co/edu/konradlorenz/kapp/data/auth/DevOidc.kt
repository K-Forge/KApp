package co.edu.konradlorenz.kapp.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import co.edu.konradlorenz.kapp.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Debug builds only: signing in through the fake OIDC server the backend's compose file starts
 * (app/backend/microservices/fake-oidc/README.md), so a debug build can run against the real
 * services on the developer's computer instead of the mocks.
 *
 * Off unless the build sets `kapp.dev.oidcIssuer` - see "Running against the local backend" in the
 * module README. src/release has an object of the same name that always answers null, so none of
 * this reaches a release build.
 */
internal object DevOidc {

    /** The fake server's client id for the mobile apps; auth-service accepts it as an audience. */
    const val CLIENT_ID = "kapp-app-dev"

    /** Handled by [OidcRedirectActivity], declared in src/debug/AndroidManifest.xml. */
    const val REDIRECT_URI = "kapp-dev://oidc/callback"

    fun signIn(context: Context): MicrosoftSignIn? =
        BuildConfig.DEV_OIDC_ISSUER.takeIf { it.isNotBlank() }?.let { DevOidcSignIn(it.trimEnd('/')) }

    /** The redirect the browser hands back, which [DevOidcSignIn.idToken] is waiting for. */
    @Volatile
    var pending: CompletableDeferred<Uri>? = null
}

/**
 * The authorization code flow with PKCE, which is what the fake server and Microsoft both expect
 * of a public client. The login page opens in the browser; whoever is typed there - `estudiante`,
 * `profesor` or `administrativo` - is who signs in, so there is no profile picker in the app.
 */
private class DevOidcSignIn(private val issuer: String) : MicrosoftSignIn {

    override val isFake = false

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun idToken(activity: Activity): String {
        val discovery = getJson("$issuer/.well-known/openid-configuration")
        val authorizeEndpoint = discovery.string("authorization_endpoint")
        val tokenEndpoint = discovery.string("token_endpoint")

        val verifier = Pkce.verifier()
        val state = Pkce.verifier()
        val redirect = CompletableDeferred<Uri>()
        DevOidc.pending = redirect

        val authorize = Uri.parse(authorizeEndpoint).buildUpon()
            .appendQueryParameter("client_id", DevOidc.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", DevOidc.REDIRECT_URI)
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        cancelIfAbandoned(activity, redirect)
        activity.startActivity(Intent(Intent.ACTION_VIEW, authorize))
        val answer = try {
            redirect.await()
        } finally {
            DevOidc.pending = null
        }

        if (answer.getQueryParameter("state") != state) {
            throw MicrosoftSignInException(IllegalStateException("The redirect's state does not match"))
        }
        answer.getQueryParameter("error")?.let { error ->
            throw if (error == "access_denied") {
                SignInCancelledException()
            } else {
                MicrosoftSignInException(IllegalStateException("The fake OIDC server answered $error"))
            }
        }
        val code = answer.getQueryParameter("code")
            ?: throw MicrosoftSignInException(IllegalStateException("The redirect carries no code"))
        return exchange(tokenEndpoint, code, verifier)
    }

    /** The fake server keeps no session the app has to end: each sign-in shows its login page. */
    override suspend fun signOut() = Unit

    /**
     * Backing out of the browser never reaches [OidcRedirectActivity], so the redirect would be
     * waited for forever and the button would say "Ingresando..." until the app was killed. When
     * the app comes back to the front with no redirect, the sign-in is treated as cancelled.
     *
     * The redirect, when there is one, completes before the app's activity resumes - the redirect
     * activity runs first - and the short delay covers the order the system delivers them in.
     */
    private fun cancelIfAbandoned(activity: Activity, redirect: CompletableDeferred<Uri>) {
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle ?: return
        var left = false
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    // An observer added to a resumed activity is replayed up to ON_RESUME at once,
                    // so a resume only counts after the activity has gone to the background.
                    Lifecycle.Event.ON_PAUSE -> left = true
                    Lifecycle.Event.ON_RESUME -> if (left) {
                        lifecycle.removeObserver(this)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!redirect.isCompleted) redirect.completeExceptionally(SignInCancelledException())
                        }, 1_000)
                    }
                    Lifecycle.Event.ON_DESTROY -> lifecycle.removeObserver(this)
                    else -> Unit
                }
            }
        }
        lifecycle.addObserver(observer)
    }

    private suspend fun exchange(tokenEndpoint: String, code: String, verifier: String): String {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", DevOidc.REDIRECT_URI)
            .add("client_id", DevOidc.CLIENT_ID)
            .add("code_verifier", verifier)
            .build()
        return call(Request.Builder().url(tokenEndpoint).post(form).build()).string("id_token")
    }

    private suspend fun getJson(url: String): JsonObject = call(Request.Builder().url(url).build())

    private suspend fun call(request: Request): JsonObject = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            // Most likely the backend is not running, or the issuer is not reachable from here.
            throw MicrosoftUnreachableException(e)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) {
                throw MicrosoftSignInException(IllegalStateException("${request.url} answered ${it.code}: $body"))
            }
            json.parseToJsonElement(body).jsonObject
        }
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.content
            ?: throw MicrosoftSignInException(IllegalStateException("The fake OIDC server sent no $name"))
}

/** RFC 7636: a random verifier, and the SHA-256 challenge of it the server checks it against. */
internal object Pkce {

    private val random = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes, 43 characters once encoded: the shortest verifier RFC 7636 allows. */
    fun verifier(): String = ByteArray(32).also(random::nextBytes).let(base64Url::encodeToString)

    fun challenge(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
