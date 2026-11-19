package co.edu.konradlorenz.kapp.data.visitor

import co.edu.konradlorenz.kapp.data.network.AuthApi
import co.edu.konradlorenz.kapp.data.network.KAppApi
import co.edu.konradlorenz.kapp.data.network.VisitorPassRedemptionRequest
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/**
 * Redeems a visitor day pass (ADR 0007) and says how it went, in the terms the screen needs.
 *
 * The map-only token the pass gives becomes a visitor session in [session] (#232): the app opens
 * on the map, and the session ends by itself when the token does, 24 hours later.
 */
class VisitorPassRepository(private val auth: AuthApi, private val session: SessionManager) {

    suspend fun redeem(code: String, request: VisitorPassRedemptionRequest): Redemption = try {
        session.startVisitorSession(auth.redeemVisitorPass(code, request))
        Redemption.Redeemed
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            404 -> Redemption.NotFound
            409 -> conflictOf(e.response()?.errorBody()?.string())
            else -> Redemption.Failed
        }
    } catch (_: IOException) {
        Redemption.Offline
    } catch (_: Exception) {
        Redemption.Failed
    }

    companion object {
        /**
         * A `409`'s `ApiError` names its reason in `details[0].issue`: `already-redeemed` or
         * `expired`. Anything else, or a body that cannot be read, is a generic failure.
         */
        fun conflictOf(body: String?): Redemption {
            val issue = runCatching {
                KAppApi.json.parseToJsonElement(body.orEmpty()).jsonObject.getValue("details")
                    .jsonArray.first().jsonObject.getValue("issue").jsonPrimitive.content
            }.getOrNull()
            return when (issue) {
                "already-redeemed" -> Redemption.AlreadyUsed
                "expired" -> Redemption.Expired
                else -> Redemption.Failed
            }
        }
    }
}

/** How a redemption went. */
enum class Redemption { Redeemed, NotFound, AlreadyUsed, Expired, Offline, Failed }
