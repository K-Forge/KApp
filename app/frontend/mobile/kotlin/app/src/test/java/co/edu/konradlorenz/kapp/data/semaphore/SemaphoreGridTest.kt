package co.edu.konradlorenz.kapp.data.semaphore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How the semáforo is drawn out of the pensum, the student's statuses, the eligible list and a
 * plan - the rules of docs/api/semaphore.openapi.yaml 2.0.0 that the client applies itself.
 */
class SemaphoreGridTest {

    private fun course(code: String, level: Int, elective: Boolean = false, sinu: String? = code) =
        PensumCourse(
            pensumItemCode = code,
            sinuCode = sinu,
            name = "Course $code",
            level = level,
            credits = 3,
            weeklyHours = 4.0,
            totalHours = 64,
            area = "CB",
            isElectiveSlot = elective,
        )

    private fun entry(code: String, status: String, resolved: String? = null) = StudentProgressCourse(
        pensumItemCode = code,
        status = status,
        sinuStatus = status,
        resolvedSinuCode = resolved,
        resolvedName = resolved?.let { "Elective $it" },
    )

    private val pensum = Pensum(
        pensumCode = "1015",
        programCode = "506",
        programName = "Ingeniería de Sistemas",
        totalCredits = 18,
        levels = 3,
        areas = listOf(PensumArea("CB", "Ciencias Básicas", "#539392", 18)),
        courses = listOf(
            course("A", 1),
            course("B", 1),
            course("C", 2),
            course("D", 2),
            course("E", 3, elective = true, sinu = null),
            course("F", 3),
        ),
    )

    private fun semaphore(vararg entries: StudentProgressCourse) = StudentSemaphore(
        programCode = "506",
        pensumCode = "1015",
        currentLevel = 2,
        source = "SINU",
        readAt = "2026-10-05T14:32:11Z",
        courses = entries.toList(),
    )

    private fun statuses(levels: List<SemaphoreLevel>) =
        levels.flatMap { it.items }.associate { it.course.pensumItemCode to it.status }

    @Test
    fun `paints the five statuses, and splits pending into eligible and blocked`() {
        val levels = buildSemaphore(
            pensum,
            semaphore(
                entry("A", "PASSED"),
                entry("B", "FAILED"),
                entry("C", "IN_PROGRESS"),
                entry("D", "POSTPONED"),
                entry("E", "PENDING"),
            ),
            eligible = setOf("E"),
            plan = null,
        )

        assertEquals(
            mapOf(
                "A" to CourseStatus.Passed,
                "B" to CourseStatus.Failed,
                "C" to CourseStatus.InProgress,
                "D" to CourseStatus.Postponed,
                "E" to CourseStatus.Eligible,
                // No entry at all: pending, and not eligible, so blocked.
                "F" to CourseStatus.Blocked,
            ),
            statuses(levels),
        )
    }

    @Test
    fun `a status this build does not know is not a failure`() {
        val levels = buildSemaphore(pensum, semaphore(entry("A", "HOMOLOGADA")), emptySet(), null)
        assertEquals(CourseStatus.Other, statuses(levels)["A"])
    }

    @Test
    fun `the pensum decides what is drawn`() {
        val levels = buildSemaphore(pensum, semaphore(entry("ZZZ", "PASSED")), emptySet(), null)

        assertEquals(listOf(1, 2, 3), levels.map { it.level })
        assertEquals(6, levels.sumOf { it.items.size })
    }

    @Test
    fun `shows sinuCode, the course that filled a slot, and never pensumItemCode`() {
        val levels = buildSemaphore(pensum, semaphore(entry("E", "PASSED", resolved = "59211")), emptySet(), null)
        val items = levels.flatMap { it.items }.associateBy { it.course.pensumItemCode }

        assertEquals("A", items.getValue("A").displayCode)
        assertEquals("59211", items.getValue("E").displayCode)
        assertEquals("Elective 59211", items.getValue("E").displayName)
    }

    @Test
    fun `an unfilled slot with no sinuCode shows no code at all`() {
        val levels = buildSemaphore(pensum, semaphore(), emptySet(), null)
        assertNull(levels.flatMap { it.items }.first { it.course.pensumItemCode == "E" }.displayCode)
    }

    @Test
    fun `a plan draws a moved course at its planned level, and remembers where from`() {
        val plan = AcademicPlan(
            id = "p",
            name = "Mi plan",
            pensumCode = "1015",
            primary = true,
            placements = listOf(Placement("F", plannedLevel = 1)),
        )

        val levels = buildSemaphore(pensum, semaphore(), emptySet(), plan)
        val moved = levels.first { it.level == 1 }.items.first { it.course.pensumItemCode == "F" }

        assertEquals(3, moved.movedFrom)
        assertEquals(listOf("E"), levels.first { it.level == 3 }.items.map { it.course.pensumItemCode })
    }

    @Test
    fun `a plan can take a course past the last level`() {
        val plan = AcademicPlan("p", "Mi plan", "1015", true, listOf(Placement("F", plannedLevel = 5)))
        val levels = buildSemaphore(pensum, semaphore(), emptySet(), plan)
        assertEquals(listOf(1, 2, 3, 4, 5), levels.map { it.level })
    }

    @Test
    fun `a placement on a course SINU has since passed is ignored`() {
        val plan = AcademicPlan("p", "Mi plan", "1015", true, listOf(Placement("A", plannedLevel = 3)))
        val levels = buildSemaphore(pensum, semaphore(entry("A", "PASSED")), emptySet(), plan)
        val a = levels.flatMap { it.items }.first { it.course.pensumItemCode == "A" }

        assertEquals(1, a.level)
        assertNull(a.movedFrom)
    }

    @Test
    fun `an elective chosen in a plan comes with whether it is offered`() {
        val plan = AcademicPlan(
            "p", "Mi plan", "1015", true,
            listOf(Placement("E", plannedLevel = 3, electiveSinuCode = "59214", electiveOffered = false)),
        )
        val levels = buildSemaphore(pensum, semaphore(), emptySet(), plan)
        val e = levels.flatMap { it.items }.first { it.course.pensumItemCode == "E" }

        assertEquals(ChosenElective("59214", offered = false), e.chosenElective)
        assertNull(e.movedFrom)
    }

    @Test
    fun `only what is still ahead can be planned`() {
        assertEquals(
            listOf(CourseStatus.Eligible, CourseStatus.Blocked, CourseStatus.Failed, CourseStatus.Postponed, CourseStatus.Other),
            CourseStatus.entries.filter { it.plannable },
        )
    }

    @Test
    fun `periods, grades and hours as people read them`() {
        assertEquals("2019-1", formatPeriod("20191"))
        assertEquals("something", formatPeriod("something"))
        assertEquals("4,1", formatGrade(41))
        assertEquals("5,0", formatGrade(50))
        assertEquals("4", formatHours(4.0))
        assertEquals("4,5", formatHours(4.5))
    }
}
