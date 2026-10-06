package co.edu.konradlorenz.kapp.data.schedule

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/api/schedule.openapi.yaml, as far as the app uses it. Read-only: there is no route to build
 * a timetable, add a course or change a room - that is done in SINU. A student and a professor read
 * their own; anybody else gets `403`, which is why the tab is theirs alone.
 */
interface ScheduleApi {

    /** The current period's timetable. `404` when SINU has none for the caller. */
    @GET("api/schedule/me")
    suspend fun mySchedule(): Schedule

    /** The classes on [date] (`YYYY-MM-DD`), sorted by start. What Inicio shows. */
    @GET("api/schedule/me/day")
    suspend fun myDay(@Query("date") date: String): List<ClassOccurrence>

    /** The week [date] falls in, Monday to Sunday. `null` is the current week. */
    @GET("api/schedule/me/week")
    suspend fun myWeek(@Query("date") date: String? = null): WeekAgenda
}

/**
 * docs/api/map.openapi.yaml, the one call a class needs: the room it is in. [buildingCode] is never
 * optional here although the API allows leaving it out - the same room number exists in more than
 * one building (EC and BI both have a 301), and without it the answer is a `409`.
 */
interface MapApi {

    @GET("api/map/spaces/{code}")
    suspend fun space(
        @Path("code") room: String,
        @Query("buildingCode") buildingCode: String,
    ): SpaceDetail
}

/** `SpaceDetail`, as far as the room sheet reads it: the space, its floor and its building. */
@Serializable
data class SpaceDetail(
    val code: String,
    val name: String? = null,
    val typeName: String? = null,
    val capacity: Int? = null,
    /** `STEP_FREE`, `STEPS` and so on, after taking `accessVia` into account. */
    val effectiveAccessibility: String? = null,
    /** How to get there, when it is not obvious: "Solo por la escalera norte". */
    val note: String? = null,
    val floor: SpaceFloor,
    val building: SpaceBuilding,
)

@Serializable
data class SpaceFloor(val code: String, val name: String, val level: Double)

@Serializable
data class SpaceBuilding(val code: String, val name: String, val campus: String? = null)
