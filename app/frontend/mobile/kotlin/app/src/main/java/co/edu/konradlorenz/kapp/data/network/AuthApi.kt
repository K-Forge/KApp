package co.edu.konradlorenz.kapp.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * docs/api/auth.openapi.yaml, as far as the client uses it. Paths are the gateway's.
 *
 * Every call here is public: the proof travels in the body, not in an `Authorization` header. That
 * is why SessionManager calls them through a KAppApi of its own, with no token and no renewal -
 * a renewal that went through the renewing client would wait on itself.
 */
interface AuthApi {

    @GET("auth/health")
    suspend fun health(): HealthStatus

    @POST("auth/microsoft")
    suspend fun signInWithMicrosoft(@Body request: MicrosoftSignInRequest): TokenResponse

    /** Rotates: the refresh token sent stops working, and both tokens in the answer replace it. */
    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    /** Always `204`, even for a token that is already unknown, so signing out cannot fail. */
    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>
}
