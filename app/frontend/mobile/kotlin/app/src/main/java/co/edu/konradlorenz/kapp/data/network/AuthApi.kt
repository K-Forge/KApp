package co.edu.konradlorenz.kapp.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** docs/api/auth.openapi.yaml, as far as the client uses it. Paths are the gateway's. */
interface AuthApi {

    @GET("auth/health")
    suspend fun health(): HealthStatus

    @POST("auth/microsoft")
    suspend fun signInWithMicrosoft(@Body request: MicrosoftSignInRequest): TokenResponse
}
