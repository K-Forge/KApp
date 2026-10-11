package co.edu.konradlorenz.kapp.data.semaphore

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import kotlin.coroutines.cancellation.CancellationException

/** Everything the semáforo is drawn from, read together. */
data class SemaphoreData(
    val semaphore: StudentSemaphore,
    val summary: ProgressSummary,
    val pensum: Pensum,
    /** `pensumItemCode`s of `GET /api/semaphore/me/eligible`. */
    val eligible: Set<String>,
    /** The primary one first, as the API sends them. */
    val plans: List<AcademicPlan>,
)

sealed interface SemaphoreState {
    data object Loading : SemaphoreState

    data class Ready(val data: SemaphoreData) : SemaphoreState

    /** `404`: SINU has no program for this person. Not an error to retry. */
    data object NoProgram : SemaphoreState

    data object Failed : SemaphoreState
}

/**
 * The student's semáforo and plans (docs/api/semaphore.openapi.yaml), read once and shared by
 * Inicio's semester card and the Semáforo tab. A student only: everybody else gets `403` here,
 * which is why neither screen asks for anybody else.
 *
 * **Plans are applied here, not taken from the answer.** A placement that the server accepted is
 * written into the plan already held, which is exactly what a real server answers - and against the
 * mocks, which store nothing and answer the same example to every write, it is the only way a move
 * shows up at all.
 */
class SemaphoreRepository(private val api: SemaphoreApi) {

    private val loading = Mutex()
    private val current = MutableStateFlow<SemaphoreState>(SemaphoreState.Loading)
    private var bank: List<ElectiveOffering>? = null

    val state: StateFlow<SemaphoreState> = current.asStateFlow()

    /** Reads it all, unless it is already here. Never throws, short of cancellation. */
    suspend fun load(): SemaphoreState = loading.withLock {
        current.value.takeIf { it is SemaphoreState.Ready }?.let { return@withLock it }
        current.value = SemaphoreState.Loading
        current.value = try {
            SemaphoreState.Ready(read())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) SemaphoreState.NoProgram else SemaphoreState.Failed
        } catch (_: Exception) {
            SemaphoreState.Failed
        }
        current.value
    }

    /** The pensum code comes from the semáforo, so that one goes first and the rest together. */
    private suspend fun read(): SemaphoreData = coroutineScope {
        val semaphore = api.mySemaphore()
        val summary = async { api.mySummary() }
        val pensum = async { api.pensum(semaphore.pensumCode) }
        val eligible = async { api.myEligible() }
        val plans = async { api.myPlans() }
        SemaphoreData(
            semaphore = semaphore,
            summary = summary.await(),
            pensum = pensum.await(),
            eligible = eligible.await().mapTo(mutableSetOf()) { it.pensumItemCode },
            plans = plans.await(),
        )
    }

    /** This semester's elective bank for the student's pensum, read once. `null` if it failed. */
    suspend fun electives(): List<ElectiveOffering>? {
        bank?.let { return it }
        val data = ready() ?: return null
        return attempt { api.electives(data.pensum.pensumCode) }?.also { bank = it }
    }

    /** Creates a plan, which starts empty. The new plan, or `null` if it was refused. */
    suspend fun createPlan(name: String): AcademicPlan? {
        val data = ready() ?: return null
        val created = attempt { api.createPlan(AcademicPlanRequest(name, data.pensum.pensumCode)) }
            ?: return null
        // As the request asked: a new plan has this name and no placements. The mocks answer a
        // fixed example, with the id of the plan already listed, which is why it replaces by id.
        val plan = created.copy(name = name, placements = emptyList())
        update { it.copy(plans = it.plans.filterNot { p -> p.id == plan.id } + plan) }
        return plan
    }

    suspend fun deletePlan(planId: String): Boolean {
        attempt { api.deletePlan(planId).also { if (!it.isSuccessful) throw HttpException(it) } }
            ?: return false
        update { it.copy(plans = it.plans.filterNot { p -> p.id == planId }) }
        return true
    }

    /**
     * Puts an item at [level] in a plan, with [elective] chosen when it is an elective slot.
     * Whether the bank offers that elective is read off the bank already loaded, if it is.
     */
    suspend fun place(planId: String, item: PensumCourse, level: Int, elective: String?): Boolean {
        attempt { api.place(planId, item.pensumItemCode, PlacementRequest(level, elective)) }
            ?: return false
        val placement = Placement(
            pensumItemCode = item.pensumItemCode,
            plannedLevel = level,
            electiveSinuCode = elective,
            electiveOffered = elective?.let { code -> bank?.any { it.sinuCode == code } },
        )
        updatePlan(planId) { plan ->
            plan.copy(placements = plan.placements.filterNot { it.pensumItemCode == item.pensumItemCode } + placement)
        }
        return true
    }

    /** Takes an item back to its pensum level, and an elective slot back to no chosen course. */
    suspend fun reset(planId: String, item: PensumCourse): Boolean {
        attempt { api.resetPlacement(planId, item.pensumItemCode) } ?: return false
        updatePlan(planId) { plan ->
            plan.copy(placements = plan.placements.filterNot { it.pensumItemCode == item.pensumItemCode })
        }
        return true
    }

    /** Forgets everything. Called when the session ends. */
    fun clear() {
        current.value = SemaphoreState.Loading
        bank = null
    }

    private fun ready(): SemaphoreData? = (current.value as? SemaphoreState.Ready)?.data

    private fun update(change: (SemaphoreData) -> SemaphoreData) {
        current.update { state ->
            if (state is SemaphoreState.Ready) SemaphoreState.Ready(change(state.data)) else state
        }
    }

    private fun updatePlan(planId: String, change: (AcademicPlan) -> AcademicPlan) = update { data ->
        data.copy(plans = data.plans.map { if (it.id == planId) change(it) else it })
    }

    /** [call]'s answer, or `null` when it failed for any reason but cancellation. */
    private suspend fun <T> attempt(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
