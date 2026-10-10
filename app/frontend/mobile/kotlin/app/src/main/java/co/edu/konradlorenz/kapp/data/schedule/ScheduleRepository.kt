package co.edu.konradlorenz.kapp.data.schedule

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

/** What a read of the timetable ended in. */
sealed interface ScheduleResult<out T> {
    data class Ready<T>(val value: T) : ScheduleResult<T>

    /** `404`: SINU has no timetable for this person this period. A state, not an error. */
    data object NoSchedule : ScheduleResult<Nothing>

    data object Failed : ScheduleResult<Nothing>
}

/**
 * The timetable (docs/api/schedule.openapi.yaml): the whole schedule once - for `source` and the
 * person's period - and the resolved day and week views as they are asked for. Weeks are kept by
 * the date asked, so paging back and forth does not read them again.
 *
 * The server resolves which meetings happen on a date and which room is in force; nothing here
 * reimplements that, as the contract asks.
 */
class ScheduleRepository(private val api: ScheduleApi, private val map: MapApi) {

    private val loading = Mutex()
    private val current = MutableStateFlow<ScheduleResult<Schedule>?>(null)
    private val weeks = mutableMapOf<String, WeekAgenda>()

    /** The whole schedule, `null` until it is read. */
    val schedule: StateFlow<ScheduleResult<Schedule>?> = current.asStateFlow()

    suspend fun loadSchedule(): ScheduleResult<Schedule> = loading.withLock {
        current.value?.takeIf { it is ScheduleResult.Ready }?.let { return@withLock it }
        read { api.mySchedule() }.also { current.value = it }
    }

    suspend fun day(date: LocalDate): ScheduleResult<List<ClassOccurrence>> =
        read { api.myDay(date.toString()) }

    suspend fun week(date: LocalDate): ScheduleResult<WeekAgenda> {
        weeks[date.toString()]?.let { return ScheduleResult.Ready(it) }
        return read { api.myWeek(date.toString()) }.also {
            if (it is ScheduleResult.Ready) weeks[date.toString()] = it.value
        }
    }

    /** The room of a class, for its sheet. `null` if it could not be read. */
    suspend fun space(room: String, buildingCode: String): SpaceDetail? = try {
        map.space(room, buildingCode)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Forgets everything. Called when the session ends. */
    fun clear() {
        current.value = null
        weeks.clear()
    }

    private suspend fun <T> read(call: suspend () -> T): ScheduleResult<T> = try {
        ScheduleResult.Ready(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 404) ScheduleResult.NoSchedule else ScheduleResult.Failed
    } catch (_: Exception) {
        ScheduleResult.Failed
    }
}

/** The seven keys of `WeekAgenda.days`, Monday first, as the contract runs its weeks. */
val WeekDays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

/** `"18:15"` as a time. The contract's `LocalTime` is always `HH:mm`. */
fun parseTime(time: String): LocalTime = LocalTime.parse(time)

/**
 * The classes of a day split the way Inicio draws them: the first one not over yet, and the ones
 * after it. `null` when every class of [classes] has ended by [now].
 */
fun nextClass(classes: List<ClassOccurrence>, now: LocalTime): Pair<ClassOccurrence, List<ClassOccurrence>>? {
    val index = classes.indexOfFirst { parseTime(it.endTime) > now }
    if (index < 0) return null
    return classes[index] to classes.drop(index + 1)
}

/** Minutes from [now] to [occurrence]'s start; `null` when it has already begun. */
fun minutesUntil(occurrence: ClassOccurrence, now: LocalTime): Int? {
    val start = parseTime(occurrence.startTime)
    if (start <= now) return null
    return ((start.toSecondOfDay() - now.toSecondOfDay()) / 60)
}
