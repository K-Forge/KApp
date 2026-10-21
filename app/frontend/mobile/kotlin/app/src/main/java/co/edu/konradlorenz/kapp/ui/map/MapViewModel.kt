package co.edu.konradlorenz.kapp.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.map.Building
import co.edu.konradlorenz.kapp.data.schedule.MapApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Mapa, for now: the campus's buildings out of `GET /api/map/buildings`. */
class MapViewModel(private val map: MapApi) : ViewModel() {

    var state by mutableStateOf<BuildingsState>(BuildingsState.Loading)
        private set

    init {
        load()
    }

    fun retry() {
        state = BuildingsState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            state = try {
                BuildingsState.Ready(map.buildings())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BuildingsState.Failed
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MapViewModel((this[APPLICATION_KEY] as KAppApplication).container.api.map) }
        }
    }
}

sealed interface BuildingsState {
    data object Loading : BuildingsState
    data class Ready(val buildings: List<Building>) : BuildingsState
    data object Failed : BuildingsState
}
