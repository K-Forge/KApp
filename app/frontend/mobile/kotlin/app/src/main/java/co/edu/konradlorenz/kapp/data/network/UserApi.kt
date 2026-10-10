package co.edu.konradlorenz.kapp.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH

/** docs/api/user.openapi.yaml, as far as the client uses it. Every call needs a bearer token. */
interface UserApi {

    /**
     * [prefer] is for the mocks only: `example=professor` picks one of the named examples in the
     * contract (`student`, `professor`, `staffAdmin`). `null` sends no header, which is what every
     * call against a real server does.
     */
    @GET("api/users/me")
    suspend fun me(@Header("Prefer") prefer: String? = null): UserProfile

    /** Changes the avatar, the one thing a person edits about their own profile. */
    @PATCH("api/users/me")
    suspend fun updateMe(@Body update: UserProfileUpdate): UserProfile
}
