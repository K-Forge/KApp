package co.edu.konradlorenz.kapp.data.schedule

import co.edu.konradlorenz.kapp.data.network.KAppApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** The month grid, and which of its days have classes, against the demo's timetable. */
class MonthGridTest {

    @Test
    fun `a month starts on its own weekday, Monday first`() {
        // 1 September 2026 is a Tuesday.
        val grid = monthGrid(YearMonth.of(2026, 9))
        assertNull(grid[0][0])
        assertEquals(LocalDate.of(2026, 9, 1), grid[0][1])
        assertEquals(5, grid.size)
        assertTrue(grid.all { it.size == 7 })
    }

    @Test
    fun `a month that starts late in the week runs to six rows`() {
        // 1 August 2026 is a Saturday and the month has 31 days.
        val grid = monthGrid(YearMonth.of(2026, 8))
        assertEquals(6, grid.size)
        assertEquals(LocalDate.of(2026, 8, 1), grid[0][5])
        assertEquals(LocalDate.of(2026, 8, 31), grid[5][0])
        assertNull(grid[5][1])
    }

    @Test
    fun `a February that starts on Monday fits four rows exactly`() {
        val grid = monthGrid(YearMonth.of(2027, 2))
        assertEquals(4, grid.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid[0][0])
        assertEquals(LocalDate.of(2027, 2, 28), grid[3][6])
    }

    private val schedule: Schedule = KAppApi.json.decodeFromString(
        File("src/demo/assets/demo/get/api/schedule/me.json").readText(),
    )

    @Test
    fun `a class counts on its weekday inside its periods`() {
        val september = classesPerDay(schedule, YearMonth.of(2026, 9))
        // Mondays: Desarrollo de aplicaciones móviles, in every September period.
        listOf(7, 14, 21, 28).forEach { assertEquals(1, september[LocalDate.of(2026, 9, it)]) }
        // Wednesdays and Saturdays: Ingeniería de Software II and Metodología de investigación.
        assertEquals(1, september[LocalDate.of(2026, 9, 23)])
        assertEquals(1, september[LocalDate.of(2026, 9, 26)])
        // Tuesday has nothing.
        assertNull(september[LocalDate.of(2026, 9, 22)])
    }

    @Test
    fun `the weeks between periods have no class`() {
        val august = classesPerDay(schedule, YearMonth.of(2026, 8))
        // Its Monday periods end on 10 August and start again on 24 August.
        assertEquals(1, august[LocalDate.of(2026, 8, 10)])
        assertNull(august[LocalDate.of(2026, 8, 17)])
        assertEquals(1, august[LocalDate.of(2026, 8, 24)])
    }

    @Test
    fun `nothing outside the sections' dates`() {
        assertTrue(classesPerDay(schedule, YearMonth.of(2026, 12)).isEmpty())
    }
}
