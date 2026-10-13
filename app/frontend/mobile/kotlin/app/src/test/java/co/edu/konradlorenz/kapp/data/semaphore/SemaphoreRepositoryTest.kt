package co.edu.konradlorenz.kapp.data.semaphore

import co.edu.konradlorenz.kapp.ui.home.SemesterState
import co.edu.konradlorenz.kapp.ui.home.semesterOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * How the semáforo is read, and how plan writes land - against a fake that behaves like the mocks:
 * every write answers the same fixed plan, whatever was asked.
 */
class SemaphoreRepositoryTest {

    private val api = FakeSemaphore()
    private val repository = SemaphoreRepository(api)

    private fun data() = (repository.state.value as SemaphoreState.Ready).data

    @Test
    fun `reads the semaforo, then the pensum it names, the summary, eligible and plans`() = runBlocking {
        val state = repository.load()

        assertTrue(state is SemaphoreState.Ready)
        assertEquals(listOf("1015"), api.pensumsAsked)
        assertEquals(setOf("E"), data().eligible)
    }

    @Test
    fun `no program in SINU is its own state`() = runBlocking {
        api.semaphoreError = HttpException(Response.error<Any>(404, "".toResponseBody()))
        assertEquals(SemaphoreState.NoProgram, repository.load())
    }

    @Test
    fun `anything else is a failure, and loading again retries`() = runBlocking {
        api.semaphoreError = IOException("offline")
        assertEquals(SemaphoreState.Failed, repository.load())

        api.semaphoreError = null
        assertTrue(repository.load() is SemaphoreState.Ready)
    }

    @Test
    fun `a new plan has the name asked for and nothing moved`() = runBlocking {
        repository.load()

        val plan = repository.createPlan("Plan de verano")

        assertEquals("Plan de verano", plan?.name)
        assertEquals(emptyList<Placement>(), plan?.placements)
        // The mock answers the id of the plan already listed: it replaces it rather than doubling.
        assertEquals(1, data().plans.size)
    }

    @Test
    fun `moving a course writes the move into the plan held, not the server's example`() = runBlocking {
        repository.load()
        val f = data().pensum.courses.first { it.pensumItemCode == "F" }

        assertTrue(repository.place("p1", f, level = 1, elective = null))

        assertEquals(listOf(Placement("F", 1, null, null)), data().plans.single().placements)
        assertEquals(listOf("F" to PlacementRequest(1, null)), api.placed)
    }

    @Test
    fun `choosing an elective knows from the bank whether it is offered`() = runBlocking {
        repository.load()
        repository.electives()
        val slot = data().pensum.courses.first { it.isElectiveSlot }

        repository.place("p1", slot, level = 3, elective = "59211")
        repository.place("p1", slot, level = 3, elective = "99999")

        assertEquals(Placement("E", 3, "99999", false), data().plans.single().placements.single())
    }

    @Test
    fun `a reset takes the placement out`() = runBlocking {
        repository.load()
        val f = data().pensum.courses.first { it.pensumItemCode == "F" }
        repository.place("p1", f, level = 1, elective = null)

        assertTrue(repository.reset("p1", f))

        assertEquals(emptyList<Placement>(), data().plans.single().placements)
    }

    @Test
    fun `a refused write leaves the plan as it was`() = runBlocking {
        repository.load()
        api.writesFail = true
        val f = data().pensum.courses.first { it.pensumItemCode == "F" }

        assertFalse(repository.place("p1", f, level = 1, elective = null))
        assertEquals(emptyList<Placement>(), data().plans.single().placements)
    }

    @Test
    fun `deleting a plan drops it`() = runBlocking {
        repository.load()
        assertTrue(repository.deletePlan("p1"))
        assertEquals(emptyList<AcademicPlan>(), data().plans)
    }

    @Test
    fun `Inicio's card counts courses in progress off the semaforo`() = runBlocking {
        val state = repository.load()
        val card = semesterOf(state) as SemesterState.Ready

        assertEquals(1, card.coursesInProgress)
        assertEquals(8, card.level)
        assertEquals(SemesterState.Unavailable, semesterOf(SemaphoreState.Failed))
    }

    private class FakeSemaphore : SemaphoreApi {
        var semaphoreError: Exception? = null
        var writesFail = false
        val pensumsAsked = mutableListOf<String>()
        val placed = mutableListOf<Pair<String, PlacementRequest>>()

        private val example = AcademicPlan("p1", "Mi plan", "1015", true, emptyList())

        private fun course(code: String, level: Int, elective: Boolean = false) = PensumCourse(
            pensumItemCode = code,
            sinuCode = code,
            name = "Course $code",
            level = level,
            credits = 3,
            weeklyHours = 4.0,
            totalHours = 64,
            area = "CB",
            isElectiveSlot = elective,
        )

        override suspend fun mySemaphore(): StudentSemaphore {
            semaphoreError?.let { throw it }
            return StudentSemaphore(
                programCode = "506",
                pensumCode = "1015",
                currentLevel = 8,
                source = "TEST",
                readAt = "2026-10-05T14:32:11Z",
                courses = listOf(
                    StudentProgressCourse("A", "PASSED"),
                    StudentProgressCourse("C", "IN_PROGRESS"),
                ),
            )
        }

        override suspend fun mySummary() = ProgressSummary(3, 3, 12, 18, 16.7, 8)

        override suspend fun myEligible() = listOf(course("E", 3, elective = true))

        override suspend fun pensum(pensumCode: String): Pensum {
            pensumsAsked += pensumCode
            return Pensum(
                pensumCode = pensumCode,
                programCode = "506",
                programName = "Ingeniería de Sistemas",
                totalCredits = 18,
                levels = 3,
                areas = emptyList(),
                courses = listOf(course("A", 1), course("C", 2), course("E", 3, true), course("F", 3)),
            )
        }

        override suspend fun electives(pensumCode: String, period: String?) =
            listOf(ElectiveOffering("59211", "Computación en la Nube", 3, 3.0))

        override suspend fun myPlans() = listOf(example)

        override suspend fun createPlan(request: AcademicPlanRequest): AcademicPlan {
            if (writesFail) throw IOException("offline")
            return example.copy(placements = listOf(Placement("X", 8, "59211", true)))
        }

        override suspend fun deletePlan(planId: String): Response<Unit> {
            if (writesFail) throw IOException("offline")
            return Response.success(204, Unit)
        }

        override suspend fun place(
            planId: String,
            pensumItemCode: String,
            request: PlacementRequest,
        ): AcademicPlan {
            if (writesFail) throw IOException("offline")
            placed += pensumItemCode to request
            return example.copy(placements = listOf(Placement("X", 8, "59211", true)))
        }

        override suspend fun resetPlacement(planId: String, pensumItemCode: String): AcademicPlan {
            if (writesFail) throw IOException("offline")
            return example
        }
    }
}
