package co.edu.konradlorenz.kapp.ui.semaphore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.semaphore.AcademicPlan
import co.edu.konradlorenz.kapp.data.semaphore.ElectiveOffering
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreData
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreItem
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreRepository
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which arrangement the grid shows. */
sealed interface PlanSelection {
    /** The primary plan if there is one, as the contract says the app opens on; else the pensum. */
    data object Default : PlanSelection

    /** The pensum as published: the *Semáforo Original*. */
    data object Original : PlanSelection

    data class Plan(val id: String) : PlanSelection
}

/** The elective bank, read when a slot's sheet asks for it. */
sealed interface ElectiveBank {
    data object NotAsked : ElectiveBank
    data object Loading : ElectiveBank
    data class Ready(val offerings: List<ElectiveOffering>) : ElectiveBank
    data object Failed : ElectiveBank
}

/**
 * The Semáforo tab (issue #44): the student's semáforo, read-only, and the one thing they write -
 * their plans. There is no action anywhere here that changes a status or a grade, because the
 * contract has none: those are SINU's.
 */
class SemaphoreViewModel(private val repository: SemaphoreRepository) : ViewModel() {

    val state: StateFlow<SemaphoreState> = repository.state

    var selection by mutableStateOf<PlanSelection>(PlanSelection.Default)
        private set

    /** The item whose sheet is open, by `pensumItemCode`. */
    var openItem by mutableStateOf<String?>(null)
        private set

    var naming by mutableStateOf(false)
        private set

    /** A plan write in flight, so nothing fires twice. */
    var saving by mutableStateOf(false)
        private set

    /** The last plan write was refused or did not arrive. Cleared by the next one. */
    var saveFailed by mutableStateOf(false)
        private set

    var bank by mutableStateOf<ElectiveBank>(ElectiveBank.NotAsked)
        private set

    init {
        viewModelScope.launch { repository.load() }
    }

    fun retry() {
        viewModelScope.launch { repository.load() }
    }

    /** The plan the grid is drawn with under [selection], or `null` for the pensum as published. */
    fun planOf(data: SemaphoreData): AcademicPlan? = when (val chosen = selection) {
        PlanSelection.Default -> data.plans.firstOrNull { it.primary } ?: data.plans.firstOrNull()
        PlanSelection.Original -> null
        is PlanSelection.Plan -> data.plans.firstOrNull { it.id == chosen.id }
    }

    fun showOriginal() {
        selection = PlanSelection.Original
    }

    fun showPlan(id: String) {
        selection = PlanSelection.Plan(id)
    }

    fun startNaming() {
        naming = true
    }

    fun stopNaming() {
        naming = false
    }

    fun createPlan(name: String) = write {
        naming = false
        repository.createPlan(name.trim())?.also { selection = PlanSelection.Plan(it.id) } != null
    }

    fun deletePlan(plan: AcademicPlan) = write {
        repository.deletePlan(plan.id).also { if (it) selection = PlanSelection.Default }
    }

    fun open(item: SemaphoreItem) {
        openItem = item.course.pensumItemCode
        saveFailed = false
    }

    fun close() {
        openItem = null
    }

    /** Puts [item] at [level] in [plan], keeping the elective chosen for it, if any. */
    fun move(plan: AcademicPlan, item: SemaphoreItem, level: Int) = write {
        if (level == item.course.level && item.chosenElective == null) {
            repository.reset(plan.id, item.course)
        } else {
            repository.place(plan.id, item.course, level, item.chosenElective?.sinuCode)
        }
    }

    fun reset(plan: AcademicPlan, item: SemaphoreItem) = write {
        repository.reset(plan.id, item.course)
    }

    /** [code] `null` takes the chosen elective away and leaves the slot where it is. */
    fun chooseElective(plan: AcademicPlan, item: SemaphoreItem, code: String?) = write {
        if (code == null && item.movedFrom == null) {
            repository.reset(plan.id, item.course)
        } else {
            repository.place(plan.id, item.course, item.level, code)
        }
    }

    fun loadBank() {
        if (bank is ElectiveBank.Ready || bank is ElectiveBank.Loading) return
        bank = ElectiveBank.Loading
        viewModelScope.launch {
            bank = repository.electives()?.let { ElectiveBank.Ready(it) } ?: ElectiveBank.Failed
        }
    }

    private fun write(action: suspend () -> Boolean) {
        if (saving) return
        saving = true
        saveFailed = false
        viewModelScope.launch {
            saveFailed = !action()
            saving = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SemaphoreViewModel((this[APPLICATION_KEY] as KAppApplication).container.semaphore)
            }
        }
    }
}
