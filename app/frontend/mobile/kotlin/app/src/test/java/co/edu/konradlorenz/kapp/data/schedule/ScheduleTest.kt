package co.edu.konradlorenz.kapp.data.schedule

import co.edu.konradlorenz.kapp.data.map.Building
import co.edu.konradlorenz.kapp.data.map.FloorPlan
import co.edu.konradlorenz.kapp.ui.home.DayState
import co.edu.konradlorenz.kapp.ui.home.dayOf
import co.edu.konradlorenz.kapp.ui.home.withBuilding
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

/**
 * The timetable of docs/api/schedule.openapi.yaml 2.0.0 as the app reads it: Inicio's next class,
 * the null room, the 404 that is a state and not an error, and the room on the map.
 */
class ScheduleTest {

    private fun occurrence(start: String, end: String, room: String? = "405", building: String? = "EC") =
        ClassOccurrence(
            sectionCode = "4127",
            sinuCode = "59035",
            pensumItemCode = "59035",
            courseName = "DESARROLLO DE APLICACIONES MÓVILES",
            group = "51",
            professor = "GÓMEZ RESTREPO LAURA MARCELA",
            startTime = start,
            endTime = end,
            blocks = 3,
            sede = "Sede Principal",
            buildingCode = building,
            room = room,
            color = "#539392",
        )

    private val day = listOf(
        occurrence("07:00", "09:15"),
        occurrence("10:00", "12:15", room = null),
        occurrence("18:15", "20:30"),
    )

    @Test
    fun `the next class is the first one not over yet`() {
        val (next, later) = nextClass(day, LocalTime.of(9, 30))!!
        assertEquals("10:00", next.startTime)
        assertEquals(listOf("18:15"), later.map { it.startTime })
    }

    @Test
    fun `a class under way is still the next one, with no countdown`() {
        val (next, _) = nextClass(day, LocalTime.of(11, 0))!!
        assertEquals("10:00", next.startTime)
        assertNull(minutesUntil(next, LocalTime.of(11, 0)))
    }

    @Test
    fun `minutes until a class that has not started`() {
        assertEquals(25, minutesUntil(occurrence("18:15", "20:30"), LocalTime.of(17, 50)))
    }

    @Test
    fun `after the last class there is no next one`() {
        assertNull(nextClass(day, LocalTime.of(21, 0)))
    }

    @Test
    fun `Inicio keeps a class with no room, and says so rather than hiding it`() {
        val state = dayOf(ScheduleResult.Ready(day), LocalTime.of(9, 30)) as DayState.Classes
        assertNull(state.next.room)
        assertEquals("Sede Principal", state.next.building)
    }

    @Test
    fun `Inicio's day for each answer of the contract`() {
        assertEquals(DayState.NoClassesToday, dayOf(ScheduleResult.Ready(emptyList()), LocalTime.NOON))
        assertEquals(DayState.DoneForToday, dayOf(ScheduleResult.Ready(day), LocalTime.of(22, 0)))
        assertEquals(DayState.NoSchedule, dayOf(ScheduleResult.NoSchedule, LocalTime.NOON))
        assertEquals(DayState.Unavailable, dayOf(ScheduleResult.Failed, LocalTime.NOON))
    }

    @Test
    fun `404 is no timetable, anything else a failure`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        api.error = HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertEquals(ScheduleResult.NoSchedule, repository.day(LocalDate.of(2026, 9, 21)))

        api.error = IOException("offline")
        assertEquals(ScheduleResult.Failed, repository.week(LocalDate.of(2026, 9, 21)))
    }

    @Test
    fun `a week read once is not read again`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        repository.week(LocalDate.of(2026, 9, 21))
        repository.week(LocalDate.of(2026, 9, 21))
        repository.week(LocalDate.of(2026, 9, 28))

        assertEquals(listOf("2026-09-21", "2026-09-28"), api.weeksAsked)
    }

    @Test
    fun `the room is asked for with its building, always`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        val space = repository.space("301", "BI")

        assertEquals(listOf("301" to "BI"), api.spacesAsked)
        assertEquals("Bienestar Institucional", (space as RoomLookup.Found).space.building.name)
    }

    @Test
    fun `a room the map answers 404 for is not on the map yet, not a failure`() = runBlocking {
        val api = FakeSchedule().apply { error = HttpException(Response.error<Any>(404, "".toResponseBody())) }
        assertEquals(RoomLookup.NotOnMap, ScheduleRepository(api, api).space("301", "BI"))
    }

    @Test
    fun `a room the map cannot be asked for is a failure, not a crash`() = runBlocking {
        val offline = FakeSchedule().apply { error = IOException("offline") }
        assertEquals(RoomLookup.Failed, ScheduleRepository(offline, offline).space("301", "BI"))

        val broken = FakeSchedule().apply { error = HttpException(Response.error<Any>(503, "".toResponseBody())) }
        assertEquals(RoomLookup.Failed, ScheduleRepository(broken, broken).space("301", "BI"))
    }

    @Test
    fun `a room is asked for once, by building and number`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        repository.space("405", "EC")
        repository.space("405", "EC")
        repository.space("405", "BI")

        assertEquals(listOf("405" to "EC", "405" to "BI"), api.spacesAsked)
    }

    @Test
    fun `a room not on the map is remembered too, a failure is asked again`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        api.error = HttpException(Response.error<Any>(404, "".toResponseBody()))
        repository.space("612", "EC")
        api.error = null
        assertEquals(RoomLookup.NotOnMap, repository.space("612", "EC"))

        api.error = IOException("offline")
        assertEquals(RoomLookup.Failed, repository.space("405", "EC"))
        api.error = null
        assertTrue(repository.space("405", "EC") is RoomLookup.Found)

        assertEquals(listOf("612" to "EC", "405" to "EC", "405" to "EC"), api.spacesAsked)
    }

    @Test
    fun `ending the session forgets the rooms`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        repository.space("405", "EC")
        repository.clear()
        repository.space("405", "EC")

        assertEquals(2, api.spacesAsked.size)
    }

    @Test
    fun `a class's building is the map's name for it, never a code`() = runBlocking {
        val api = FakeSchedule().apply { buildingName = "Edificio Central" }
        val building = ScheduleRepository(api, api).buildingOf(occurrence("18:15", "20:30", room = "612"))

        assertEquals("Edificio Central", building)
        assertFalse(building.orEmpty().contains("EC"))
    }

    @Test
    fun `a class whose room does not resolve has no building, and keeps its sede`() = runBlocking {
        val api = FakeSchedule()
        val repository = ScheduleRepository(api, api)

        assertNull(repository.buildingOf(occurrence("07:00", "09:15", room = null)))
        assertNull(repository.buildingOf(occurrence("07:00", "09:15", building = null)))
        assertTrue("nothing to ask the map", api.spacesAsked.isEmpty())

        api.error = HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertNull(repository.buildingOf(occurrence("07:00", "09:15", room = "999")))
        api.error = IOException("offline")
        assertNull(repository.buildingOf(occurrence("07:00", "09:15", room = "998")))
    }

    @Test
    fun `Inicio's card shows the building in place of the sede once the room resolves`() {
        val next = occurrence("18:15", "20:30")
        val state = dayOf(ScheduleResult.Ready(listOf(next)), LocalTime.of(17, 0))

        val withBuilding = state.withBuilding(next, "Edificio Central") as DayState.Classes

        assertEquals("Sede Principal", (state as DayState.Classes).next.building)
        assertEquals("Edificio Central", withBuilding.next.building)
        assertEquals("405", withBuilding.next.room)
    }

    @Test
    fun `a building for a class no longer on the card changes nothing`() {
        val state = dayOf(ScheduleResult.Ready(day), LocalTime.of(17, 0))

        assertEquals(state, state.withBuilding(occurrence("07:00", "09:15"), "Edificio Central"))
        assertEquals(DayState.DoneForToday, DayState.DoneForToday.withBuilding(day.first(), "Edificio Central"))
    }

    @Test
    fun `the week runs Monday to Sunday, as the contract's keys do`() {
        assertEquals("MONDAY", WeekDays.first())
        assertEquals("SUNDAY", WeekDays.last())
        assertTrue(WeekDays.size == 7)
    }

    private class FakeSchedule : ScheduleApi, MapApi {
        var error: Exception? = null
        val weeksAsked = mutableListOf<String?>()
        val spacesAsked = mutableListOf<Pair<String, String>>()
        var buildingName = "Bienestar Institucional"

        override suspend fun mySchedule(): Schedule {
            error?.let { throw it }
            return Schedule("20262", "506", "1015", 8, true, "TEST", "2026-10-05T14:32:11Z", emptyList())
        }

        override suspend fun myDay(date: String): List<ClassOccurrence> {
            error?.let { throw it }
            return emptyList()
        }

        override suspend fun myWeek(date: String?): WeekAgenda {
            error?.let { throw it }
            weeksAsked += date
            return WeekAgenda(date.orEmpty(), date.orEmpty(), WeekDays.associateWith { emptyList() })
        }

        override suspend fun buildings(): List<Building> = error("not used here")

        override suspend fun floor(building: String, floor: String, etag: String?): Response<FloorPlan> =
            error("not used here")

        override suspend fun space(room: String, buildingCode: String): SpaceDetail {
            spacesAsked += room to buildingCode
            error?.let { throw it }
            return SpaceDetail(
                code = room,
                floor = SpaceFloor("P3", "Piso 3", 3.0),
                building = SpaceBuilding(buildingCode, buildingName),
            )
        }
    }
}
