package co.edu.konradlorenz.kapp.data.demo

import android.content.res.AssetManager
import co.edu.konradlorenz.kapp.data.network.KAppApi
import co.edu.konradlorenz.kapp.data.semaphore.AcademicPlan
import co.edu.konradlorenz.kapp.data.semaphore.AcademicPlanRequest
import java.io.IOException
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * The demo build's whole backend (issue #159): answers every API call from the contracts' examples
 * in src/demo/assets/demo, without opening a connection. The files are the Prism mocks' answers,
 * copied by scripts/capture-demo-data.sh, so the screens show what they show against the mocks.
 *
 * It keeps no state. Creating a plan or moving a course answers with the example plan, and the
 * next read shows the example again.
 */
class DemoBackend(private val assets: AssetManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // A new plan gets an id of its own and the name it was asked for, so the demo can hold
        // more than one plan and switch between them (issue #176). Everything else is a file.
        if (request.method == "POST" && request.url.encodedPath.trim('/') == "api/semaphore/me/plans") {
            return request.respond(201, newPlan(Buffer().also { request.body?.writeTo(it) }.readUtf8()))
        }
        val answer = answerFor(
            method = request.method,
            path = request.url.encodedPath,
            buildingCode = request.url.queryParameter("buildingCode"),
            prefer = request.header("Prefer"),
        )
        return when (answer) {
            DemoAnswer.NoContent -> request.respond(204, "")
            is DemoAnswer.Asset -> try {
                request.respond(200, assets.open("demo/${answer.path}").use { it.readBytes().decodeToString() })
            } catch (_: IOException) {
                // The same body the services answer with, so a screen shows its "not found" state.
                request.respond(404, """{"status":404,"error":"Not Found","path":"${request.url.encodedPath}"}""")
            }
        }
    }

    companion object {
        /**
         * The file that answers a call, or that there is nothing to answer with. Pure, so the
         * mapping is unit-tested without Android.
         *
         * Files sit at `<method>/<path>.json`. Three calls do not map one to one:
         * - `/api/users/me` picks the profile from the `Prefer` header, as Prism does, so the
         *   picker on the login decides who signs in; a `PATCH` to it answers the same profile;
         * - `GET /api/map/spaces/{room}` is per building too, from the `buildingCode` query;
         * - a placement in a plan has ids in its path, and any ids answer with the example plan.
         */
        fun answerFor(method: String, path: String, buildingCode: String?, prefer: String?): DemoAnswer {
            val verb = method.lowercase()
            val route = path.trim('/')
            return when {
                route == "auth/logout" -> DemoAnswer.NoContent
                route == "api/users/me" ->
                    DemoAnswer.Asset("get/$route.${prefer?.substringAfter("example=", "")?.ifBlank { null } ?: "student"}.json")
                route.startsWith("api/map/spaces/") ->
                    DemoAnswer.Asset("get/api/map/spaces/$buildingCode-${route.substringAfterLast('/')}.json")
                PLACEMENT.matches(route) -> DemoAnswer.Asset("put/api/semaphore/me/plans/any/placements/any.json")
                verb == "delete" -> DemoAnswer.NoContent
                else -> DemoAnswer.Asset("$verb/$route.json")
            }
        }

        /**
         * The plan `POST /api/semaphore/me/plans` creates out of its request [body]: empty, named
         * as asked, with a fresh id.
         */
        fun newPlan(body: String): String {
            val asked = KAppApi.json.decodeFromString<AcademicPlanRequest>(body)
            val plan = AcademicPlan(
                id = "demo-${UUID.randomUUID()}",
                name = asked.name,
                pensumCode = asked.pensumCode,
                primary = false,
                placements = emptyList(),
            )
            return KAppApi.json.encodeToString(plan)
        }

        private val PLACEMENT = Regex("api/semaphore/me/plans/[^/]+/placements/[^/]+")

        private val JSON = "application/json".toMediaType()

        private fun Request.respond(code: Int, body: String) = Response.Builder()
            .request(this)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 300) "OK" else "Not Found")
            .body(body.toResponseBody(JSON))
            .build()
    }
}

/** Where [DemoBackend] gets an answer from. */
sealed interface DemoAnswer {
    /** A file under src/demo/assets/demo. */
    data class Asset(val path: String) : DemoAnswer

    /** `204`, for what answers with no body: signing out and deleting a plan. */
    data object NoContent : DemoAnswer
}
