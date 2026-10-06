package co.edu.konradlorenz.kapp.ui.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.schedule.ClassOccurrence
import co.edu.konradlorenz.kapp.data.schedule.Schedule
import co.edu.konradlorenz.kapp.data.schedule.ScheduleRepository
import co.edu.konradlorenz.kapp.data.schedule.ScheduleResult
import co.edu.konradlorenz.kapp.data.schedule.SpaceDetail
import co.edu.konradlorenz.kapp.data.schedule.WeekAgenda
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The three views issue #45 asks for, all drawn from one `GET /api/schedule/me/week`. */
enum class ScheduleView { Day, TwoDays, Week }

/** The room of the class whose sheet is open. */
sealed interface RoomState {
    /** `room` is `null`: no classroom assigned for this stretch. Nothing to look up. */
    data object NoRoom : RoomState

    /** `buildingCode` is `null`: the sede is not mapped to a building yet. The room, no map. */
    data object NotMapped : RoomState

    data object Loading : RoomState
    data class Ready(val space: SpaceDetail) : RoomState
    data object Failed : RoomState
}

/**
 * Horario (issue #45): the week, read-only, as a day, two days or the whole week; and the room of a
 * class, from the map. There is no control anywhere to build a timetable or add or drop a course:
 * that is SINU's, and the contract has no route for it.
 */
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {

    /** The whole schedule: what says whether the data is `TEST`. */
    val schedule: StateFlow<ScheduleResult<Schedule>?> = repository.schedule

    /** Any date inside the week shown; the server snaps it to that week's Monday. */
    var anchor by mutableStateOf(today)
        private set

    var week by mutableStateOf<ScheduleResult<WeekAgenda>?>(null)
        private set

    var view by mutableStateOf(ScheduleView.Day)
        private set

    /** 0 is Monday. Starts on today. */
    var selectedDay by mutableStateOf(today.dayOfWeek.value - 1)
        private set

    var openClass by mutableStateOf<ClassOccurrence?>(null)
        private set

    var room by mutableStateOf<RoomState>(RoomState.NoRoom)
        private set

    private var roomJob: Job? = null

    init {
        viewModelScope.launch { repository.loadSchedule() }
        loadWeek()
    }

    fun retry() {
        viewModelScope.launch { repository.loadSchedule() }
        loadWeek()
    }

    fun show(view: ScheduleView) {
        this.view = view
    }

    fun select(day: Int) {
        selectedDay = day.coerceIn(0, 6)
    }

    fun previousWeek() {
        anchor = anchor.minusWeeks(1)
        loadWeek()
    }

    fun nextWeek() {
        anchor = anchor.plusWeeks(1)
        loadWeek()
    }

    /**
     * Opens a class's sheet and, when it has both a room and a mapped building, asks the map for
     * that room - always with `buildingCode`, since room numbers repeat across buildings.
     */
    fun open(occurrence: ClassOccurrence) {
        openClass = occurrence
        roomJob?.cancel()
        val number = occurrence.room
        val building = occurrence.buildingCode
        room = when {
            number == null -> RoomState.NoRoom
            building == null -> RoomState.NotMapped
            else -> RoomState.Loading
        }
        if (number != null && building != null) {
            roomJob = viewModelScope.launch {
                room = repository.space(number, building)?.let { RoomState.Ready(it) } ?: RoomState.Failed
            }
        }
    }

    fun close() {
        openClass = null
        roomJob?.cancel()
    }

    private fun loadWeek() {
        week = null
        val asked = anchor
        viewModelScope.launch {
            val result = repository.week(asked)
            if (asked == anchor) week = result
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ScheduleViewModel((this[APPLICATION_KEY] as KAppApplication).container.schedule)
            }
        }
    }
}
