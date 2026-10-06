package co.edu.konradlorenz.kapp.data.network

import retrofit2.http.GET

/** docs/api/user.openapi.yaml, as far as the client uses it. Every call needs a bearer token. */
interface UserApi {

    @GET("api/users/me")
    suspend fun me(): UserProfile
}
