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
import co.edu.konradlorenz.kapp.data.map.FloorPlan
import co.edu.konradlorenz.kapp.data.map.FloorRepository
import co.edu.konradlorenz.kapp.data.schedule.MapApi
import co.edu.konradlorenz.kapp.ui.map.floor.MapStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mapa: the campus's buildings out of `GET /api/map/buildings`, and a building's floors drawn one at
 * a time with the shared [style].
 */
class MapViewModel(
    private val map: MapApi,
    private val floors: FloorRepository,
    val style: MapStyle,
) : ViewModel() {

    var state by mutableStateOf<BuildingsState>(BuildingsState.Loading)
        private set

    init {
        load()
    }

    /** The building whose floors are on screen; null while the list is. */
    var opened by mutableStateOf<OpenedBuilding?>(null)
        private set

    private var floorLoad: Job? = null

    fun retry() {
        state = BuildingsState.Loading
        load()
    }

    /** Opens [building] on its lowest floor at or above the street, or its lowest one if none is. */
    fun open(building: Building) {
        val start = building.floors.firstOrNull { it.level >= 0 } ?: building.floors.firstOrNull() ?: return
        showFloor(building, start.code)
    }

    fun showFloor(building: Building, floorCode: String) {
        floorLoad?.cancel()
        opened = OpenedBuilding(building, floorCode, FloorState.Loading)
        floorLoad = viewModelScope.launch {
            val floor = try {
                FloorState.Ready(floors.floor(building.code, floorCode))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FloorState.Failed
            }
            opened = opened?.copy(floor = floor)
        }
    }

    /** Back to the list. */
    fun close() {
        floorLoad?.cancel()
        opened = null
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
            initializer {
                val app = this[APPLICATION_KEY] as KAppApplication
                val style = app.assets.open(MapStyle.ASSET).bufferedReader().use { MapStyle.parse(it.readText()) }
                MapViewModel(app.container.api.map, app.container.floors, style)
            }
        }
    }
}

sealed interface BuildingsState {
    data object Loading : BuildingsState
    data class Ready(val buildings: List<Building>) : BuildingsState
    data object Failed : BuildingsState
}

/** A building on screen, the floor chosen in it, and that floor's drawing. */
data class OpenedBuilding(val building: Building, val floorCode: String, val floor: FloorState)

sealed interface FloorState {
    data object Loading : FloorState
    data class Ready(val plan: FloorPlan) : FloorState
    data object Failed : FloorState
}
