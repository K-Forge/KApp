package co.edu.konradlorenz.kapp.data.network

import co.edu.konradlorenz.kapp.BuildConfig
import co.edu.konradlorenz.kapp.data.schedule.MapApi
import co.edu.konradlorenz.kapp.data.schedule.ScheduleApi
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreApi
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The one way into the API. Every service sits behind the same base URL with the gateway's routes,
 * so one Retrofit serves them all, and moving from the mocks to a real server changes only
 * [baseUrl] - which comes from the build type (app/build.gradle.kts).
 *
 * [accessToken] is asked for on every request. While it answers `null` no `Authorization` header is
 * sent, which is what the public endpoints expect and what a secured one answers `401` to.
 *
 * [renewAfterRejection] is asked once when a request that carried a token comes back `401`: it
 * answers the token to retry with, or `null` to let the `401` through. Both run on OkHttp's threads
 * and may block - SessionManager renews inside them.
 */
class KAppApi(
    accessToken: () -> String?,
    renewAfterRejection: ((rejected: String) -> String?)? = null,
    baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(bearer(accessToken))
        .apply { renewAfterRejection?.let { authenticator(retryOnce(it)) } }
        // Short on purpose: against the mocks, a server that has not answered in a few seconds is
        // a server that is not running, and the developer should hear about it now.
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val auth: AuthApi = retrofit.create(AuthApi::class.java)
    val users: UserApi = retrofit.create(UserApi::class.java)
    val semaphore: SemaphoreApi = retrofit.create(SemaphoreApi::class.java)
    val schedule: ScheduleApi = retrofit.create(ScheduleApi::class.java)
    val map: MapApi = retrofit.create(MapApi::class.java)

    companion object {
        /**
         * Unknown fields are ignored so a contract can grow ahead of the client. Defaults are
         * written out because some are required by the contract: without it `client: "APP"` in
         * [MicrosoftSignInRequest] would be left off and the request refused with `400`.
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun bearer(accessToken: () -> String?) = Interceptor { chain ->
            val token = accessToken()
            val request = if (token == null) {
                chain.request()
            } else {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            }
            chain.proceed(request)
        }

        /**
         * Retries a `401` once with the token [renew] answers. Once, and only for a request that
         * sent a token: a second `401` on a fresh token is a real refusal, and retrying it would
         * loop.
         */
        private fun retryOnce(renew: (String) -> String?) = Authenticator { _, response ->
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (sent == null || response.priorResponse != null) return@Authenticator null
            renew(sent)?.let {
                response.request.newBuilder().header("Authorization", "Bearer $it").build()
            }
        }
    }
}
