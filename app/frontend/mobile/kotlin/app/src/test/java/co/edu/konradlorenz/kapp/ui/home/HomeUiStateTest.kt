package co.edu.konradlorenz.kapp.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two pieces of Inicio that are arithmetic rather than layout: the greeting the band prints,
 * and the two segments of the progress bar. Both can be pinned down now, even though nothing calls
 * schedule-service or semaphore-service yet.
 */
class HomeUiStateTest {

    @Test
    fun `greets by first name and abbreviates to two initials`() {
        val student = student("Pepe Pérez")
        assertEquals("Pepe", student.firstName)
        assertEquals("PP", student.initials)
    }

    @Test
    fun `a third surname does not add a third initial`() {
        assertEquals("PP", student("Pepe Pérez Gómez").initials)
    }

    @Test
    fun `one word gives one initial rather than a doubled one`() {
        val student = student("Pepe")
        assertEquals("Pepe", student.firstName)
        assertEquals("P", student.initials)
    }

    @Test
    fun `a lowercase name still gives uppercase initials`() {
        assertEquals("PP", student("pepe pérez").initials)
    }

    @Test
    fun `extra whitespace does not become an initial`() {
        assertEquals("PP", student("  Pepe   Pérez  ").initials)
    }

    @Test
    fun `an empty name produces an empty greeting instead of failing`() {
        val student = student("   ")
        assertEquals("", student.firstName)
        assertEquals("", student.initials)
    }

    @Test
    fun `the bar segments are shares of the whole pensum, not of each other`() {
        val semester = SemesterState.Ready(
            level = 8,
            coursesInProgress = 5,
            creditsPassed = 112,
            creditsInProgress = 15,
            creditsRemaining = 15,
            totalCredits = 142,
            percentComplete = 78.9,
        )
        // The widths HomeAndroid.dc.html draws: 78.9% green, 10.6% lime.
        assertEquals(0.789f, semester.passedFraction, 0.001f)
        assertEquals(0.106f, semester.inProgressFraction, 0.001f)
    }

    @Test
    fun `an empty pensum leaves the bar empty instead of dividing by zero`() {
        val semester = SemesterState.Ready(
            level = 1,
            coursesInProgress = 0,
            creditsPassed = 0,
            creditsInProgress = 0,
            creditsRemaining = 0,
            totalCredits = 0,
            percentComplete = 0.0,
        )
        assertEquals(0f, semester.passedFraction, 0f)
        assertEquals(0f, semester.inProgressFraction, 0f)
    }
}
