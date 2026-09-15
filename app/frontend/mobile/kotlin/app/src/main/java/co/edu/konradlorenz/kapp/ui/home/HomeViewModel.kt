package co.edu.konradlorenz.kapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds what Inicio has on it.
 *
 * There is no repository and no network yet, so the state starts at [SampleHomeUiState] and never
 * changes: this class cannot fail and cannot be slow. What it does do is put the shape of the
 * screen in one place, so the day and the semester can start arriving separately without the
 * screen above it moving.
 *
 * When the calls arrive - `GET /api/schedule/me/day` in docs/api/schedule.openapi.yaml and
 * `GET /api/semaphore/me/summary` in docs/api/semaphore.openapi.yaml - each gets a coroutine that
 * writes its own half of the state. [DayState.Loading] and [SemesterState.Loading] are already
 * drawn, so the first thing to write is the failure case neither contract has a picture for yet.
 */
class HomeViewModel : ViewModel() {

    var uiState by mutableStateOf(SampleHomeUiState)
        private set
}
