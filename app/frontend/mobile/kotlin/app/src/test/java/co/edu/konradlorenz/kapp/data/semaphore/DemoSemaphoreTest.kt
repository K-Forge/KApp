package co.edu.konradlorenz.kapp.data.semaphore

import co.edu.konradlorenz.kapp.data.network.KAppApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The demo's Semáforo: the whole 1015 pensum from semaphore-service's seed, and a student's
 * semáforo that agrees with it, so a plan has pending courses to move (issue #176).
 */
class DemoSemaphoreTest {

    private inline fun <reified T> read(path: String): T =
        KAppApi.json.decodeFromString(File("src/demo/assets/demo/get/api/$path").readText())

    private val pensum: Pensum = read("catalog/pensums/1015.json")
    private val semaphore: StudentSemaphore = read("semaphore/me.json")
    private val eligible: Set<String> = read<List<PensumCourse>>("semaphore/me/eligible.json")
        .map { it.pensumItemCode }.toSet()

    private val levels = buildSemaphore(pensum, semaphore, eligible, plan = null)

    @Test
    fun `the whole pensum, nine levels, every one with courses`() {
        assertEquals(9, pensum.levels)
        assertEquals(51, pensum.courses.size)
        assertTrue(levels.all { it.items.isNotEmpty() })
    }

    @Test
    fun `every entry of the semáforo is a course of the pensum`() {
        val codes = pensum.courses.map { it.pensumItemCode }.toSet()
        assertTrue(semaphore.courses.all { it.pensumItemCode in codes })
    }

    @Test
    fun `levels before the student's own are done but for the example's own cases`() {
        val leftBehind = levels.filter { it.level < semaphore.currentLevel }
            .flatMap { it.items }
            .filter { it.status != CourseStatus.Passed }
            .map { it.course.pensumItemCode }
            .toSet()
        // Inferencia Estadística failed, and Electivas III and IV still to take: the example's.
        assertEquals(setOf("17070", "59085", "59088"), leftBehind)
    }

    @Test
    fun `there are pending courses ahead for a plan to move`() {
        val ahead = levels.filter { it.level >= semaphore.currentLevel }
            .flatMap { it.items }
            .filter { it.status.plannable }
        assertTrue(ahead.size >= 4)
    }
}
