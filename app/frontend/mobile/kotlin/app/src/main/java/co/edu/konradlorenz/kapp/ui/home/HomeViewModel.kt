package co.edu.konradlorenz.kapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.profile.ProfileRepository
import co.edu.konradlorenz.kapp.data.profile.ProfileState
import co.edu.konradlorenz.kapp.data.schedule.ClassOccurrence
import co.edu.konradlorenz.kapp.data.schedule.ScheduleRepository
import co.edu.konradlorenz.kapp.data.schedule.ScheduleResult
import co.edu.konradlorenz.kapp.data.schedule.minutesUntil
import co.edu.konradlorenz.kapp.data.schedule.nextClass
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreRepository
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreState
import co.edu.konradlorenz.kapp.data.session.ProfileRole
import co.edu.konradlorenz.kapp.data.session.SessionManager
import co.edu.konradlorenz.kapp.ui.common.hexColor
import co.edu.konradlorenz.kapp.ui.theme.Subject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Holds what Inicio has on it: the greeting from [profile], the day from [schedule] - for a student
 * or a professor - and a student's semester card from [semaphore].
 *
 * The three arrive separately, as the mockups ask: a student with no timetable still has a
 * semáforo, and none of them waits for another.
 */
class HomeViewModel(
    profile: ProfileRepository,
    session: SessionManager,
    semaphore: SemaphoreRepository,
    private val schedule: ScheduleRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> LocalTime = LocalTime::now,
) : ViewModel() {

    var uiState by mutableStateOf(
        SampleHomeUiState.copy(student = null, day = DayState.Loading, semester = SemesterState.Loading),
    )
        private set

    init {
        viewModelScope.launch {
            profile.state.collect { state ->
                // A profile that failed to load leaves the greeting without a name rather than
                // with somebody else's. Perfil is where it can be tried again.
                uiState = uiState.copy(student = (state as? ProfileState.Ready)?.profile?.toStudent())
            }
        }
        viewModelScope.launch {
            // Each card is read only for a role that has it: the others answer 403, and Inicio
            // does not draw the card for them. The role can arrive after this screen does.
            session.session.map { it?.profileRole }.distinctUntilChanged().collect { role ->
                if (role == ProfileRole.Student) launch { semaphore.load() }
                if (role == ProfileRole.Student || role == ProfileRole.Professor) launch { loadDay() }
            }
        }
        viewModelScope.launch {
            semaphore.state.collect { state -> uiState = uiState.copy(semester = semesterOf(state)) }
        }
    }

    private suspend fun loadDay() {
        // Read first, then copy: copying the state around the call would write back whatever it
        // held when the call began, undoing a greeting that arrived in the meantime.
        val day = dayOf(schedule.day(today()), now())
        uiState = uiState.copy(day = day)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KAppApplication).container
                HomeViewModel(container.profile, container.session, container.semaphore, container.schedule)
            }
        }
    }
}

/**
 * Inicio's headline card out of `GET /api/schedule/me/day`: the first class not over yet on the
 * card, the rest under "Resto del dia". A `404` is a person with no timetable this period, which
 * the mockup draws as a task, not an error.
 */
internal fun dayOf(result: ScheduleResult<List<ClassOccurrence>>, now: LocalTime): DayState = when (result) {
    ScheduleResult.NoSchedule -> DayState.NoSchedule
    ScheduleResult.Failed -> DayState.Unavailable
    is ScheduleResult.Ready -> when {
        result.value.isEmpty() -> DayState.NoClassesToday
        else -> nextClass(result.value, now)?.let { (next, later) ->
            DayState.Classes(
                next = NextClass(
                    courseName = next.courseName,
                    startTime = next.startTime,
                    endTime = next.endTime,
                    room = next.room,
                    building = next.sede,
                    color = hexColor(next.color, Subject),
                    // The badge is for the class about to start. "Empieza en 154 min" is a sum to
                    // do, and the start time is printed right under it anyway.
                    startsInMinutes = minutesUntil(next, now)?.takeIf { it <= 60 },
                ),
                later = later.map {
                    UpcomingClass(
                        courseName = it.courseName,
                        startTime = it.startTime,
                        endTime = it.endTime,
                        room = it.room,
                        building = it.sede,
                        color = hexColor(it.color, Subject),
                    )
                },
            )
        } ?: DayState.DoneForToday
    }
}

/** The semester card out of the semáforo: the summary's credits, and the courses being taken. */
internal fun semesterOf(state: SemaphoreState): SemesterState = when (state) {
    SemaphoreState.Loading -> SemesterState.Loading
    SemaphoreState.Failed, SemaphoreState.NoProgram -> SemesterState.Unavailable
    is SemaphoreState.Ready -> state.data.summary.let { summary ->
        SemesterState.Ready(
            level = summary.currentLevel,
            coursesInProgress = state.data.semaphore.courses.count { it.status == "IN_PROGRESS" },
            creditsPassed = summary.creditsPassed,
            creditsInProgress = summary.creditsInProgress,
            creditsRemaining = summary.creditsRemaining,
            totalCredits = summary.totalCredits,
            percentComplete = summary.percentComplete,
        )
    }
}
