package co.edu.konradlorenz.kapp.data.network

import kotlin.coroutines.cancellation.CancellationException

/**
 * Walks the path every later screen depends on, once, against whatever [api] points at: the
 * gateway answers, a sign-in returns a token, and that token opens a secured route.
 *
 * Debug only, and temporary. It exists so issue #43 can be checked from a device before there is a
 * real sign-in; #46 replaces the fake ID token with Microsoft's and keeps the session, and #47
 * gives the profile a screen. Nothing it obtains is kept.
 *
 * Never throws: the outcome, good or bad, comes back as one line for the log.
 */
suspend fun checkMockConnection(): String {
    var accessToken: String? = null
    val api = KAppApi(accessToken = { accessToken })
    return try {
        val health = api.auth.health()
        // The mocks accept any ID token of 20 characters or more; the real server never will.
        val tokens = api.auth.signInWithMicrosoft(MicrosoftSignInRequest(idToken = FAKE_ID_TOKEN))
        accessToken = tokens.accessToken
        val me = api.users.me()
        "auth ${health.status}, signed in as ${tokens.roles}, /api/users/me is ${me.firstName} ${me.lastName}"
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // An IOException means nothing answered; an HttpException means a call was refused.
        "API check failed: ${e.javaClass.simpleName}: ${e.message}"
    }
}

private const val FAKE_ID_TOKEN = "debug-build-fake-microsoft-id-token"
