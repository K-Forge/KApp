package co.edu.konradlorenz.kapp.data.network

import co.edu.konradlorenz.kapp.BuildConfig
import kotlinx.serialization.json.Json
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
 */
class KAppApi(
    accessToken: () -> String?,
    baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(bearer(accessToken))
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
    }
}
