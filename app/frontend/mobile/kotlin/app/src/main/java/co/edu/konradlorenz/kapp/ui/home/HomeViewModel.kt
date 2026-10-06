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
import kotlinx.coroutines.launch

/**
 * Holds what Inicio has on it.
 *
 * The greeting is the signed-in person's, from [profile]. The day and the semester are still
 * [SampleHomeUiState]'s: there is no repository behind them yet, so they cannot fail and cannot be
 * slow. What this class does do is put the shape of the screen in one place, so the day and the
 * semester can start arriving separately without the screen above it moving.
 *
 * When the calls arrive - `GET /api/schedule/me/day` in docs/api/schedule.openapi.yaml and
 * `GET /api/semaphore/me/summary` in docs/api/semaphore.openapi.yaml - each gets a coroutine that
 * writes its own half of the state. [DayState.Loading] and [SemesterState.Loading] are already
 * drawn, so the first thing to write is the failure case neither contract has a picture for yet.
 */
class HomeViewModel(profile: ProfileRepository) : ViewModel() {

    var uiState by mutableStateOf(SampleHomeUiState.copy(student = null))
        private set

    init {
        viewModelScope.launch {
            profile.state.collect { state ->
                // A profile that failed to load leaves the greeting without a name rather than
                // with somebody else's. Perfil is where it can be tried again.
                uiState = uiState.copy(student = (state as? ProfileState.Ready)?.profile?.toStudent())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel((this[APPLICATION_KEY] as KAppApplication).container.profile)
            }
        }
    }
}
