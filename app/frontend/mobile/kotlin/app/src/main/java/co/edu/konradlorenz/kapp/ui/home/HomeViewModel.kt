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
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreRepository
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreState
import co.edu.konradlorenz.kapp.data.session.ProfileRole
import co.edu.konradlorenz.kapp.data.session.SessionManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds what Inicio has on it.
 *
 * The greeting is the signed-in person's, from [profile], and the semester card a student's, from
 * [semaphore]. The day is still [SampleHomeUiState]'s until `GET /api/schedule/me/day`
 * (docs/api/schedule.openapi.yaml) has a repository behind it; [DayState.Loading] is already drawn.
 *
 * The two halves arrive separately, as the mockups ask: a student with no timetable still has a
 * semáforo, and neither waits for the other.
 */
class HomeViewModel(
    profile: ProfileRepository,
    session: SessionManager,
    semaphore: SemaphoreRepository,
) : ViewModel() {

    var uiState by mutableStateOf(
        SampleHomeUiState.copy(student = null, semester = SemesterState.Loading),
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
            // Only a student has a semáforo: anybody else gets 403, and Inicio does not draw the
            // card for them. The role can arrive after this screen does, with the profile.
            session.session.map { it?.profileRole }.distinctUntilChanged().collect { role ->
                if (role == ProfileRole.Student) semaphore.load()
            }
        }
        viewModelScope.launch {
            semaphore.state.collect { state -> uiState = uiState.copy(semester = semesterOf(state)) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KAppApplication).container
                HomeViewModel(container.profile, container.session, container.semaphore)
            }
        }
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
