package co.edu.konradlorenz.kapp

import android.app.Application
import android.content.Context
import co.edu.konradlorenz.kapp.data.auth.MicrosoftSignIn
import co.edu.konradlorenz.kapp.data.network.KAppApi
import co.edu.konradlorenz.kapp.data.session.KeystoreSessionStore
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.runBlocking

/**
 * Holds the one instance of everything that must be shared: two clients sending different tokens,
 * or two session managers renewing the same refresh token, would undo each other.
 *
 * Built by hand rather than through a DI framework: there are four objects, and a framework would
 * be more to read than they are.
 */
class AppContainer(context: Context) {

    /** The auth endpoints only. No token, no renewal - see AuthApi. */
    private val publicApi = KAppApi(accessToken = { null })

    val session = SessionManager(publicApi.auth, KeystoreSessionStore(context))

    /**
     * Everything else. It sends the session's token and renews it, blocking OkHttp's thread while
     * it does - which is what OkHttp's interceptors and authenticators are for.
     */
    val api = KAppApi(
        accessToken = { runBlocking { session.accessToken() } },
        renewAfterRejection = { rejected -> runBlocking { session.renewAfterRejection(rejected) } },
    )

    val microsoft = MicrosoftSignIn.forThisBuild(context)

    /** KApp's session first - it is the one that matters - then Microsoft's account on the device. */
    suspend fun signOut() {
        session.signOut()
        microsoft.signOut()
    }
}

class KAppApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
