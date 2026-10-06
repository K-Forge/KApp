package co.edu.konradlorenz.kapp.ui.navigation

import co.edu.konradlorenz.kapp.data.session.ProfileRole
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination.Home
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination.Map
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination.Profile
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination.Schedule
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination.Semaphore
import org.junit.Assert.assertEquals
import org.junit.Test

/** The table of tabs by profile role in issue #46, and how the role is read out of `roles`. */
class DestinationsForTest {

    @Test
    fun `a student sees every tab`() {
        assertEquals(
            listOf(Profile, Semaphore, Home, Map, Schedule),
            destinationsFor(ProfileRole.Student),
        )
    }

    @Test
    fun `a professor has no semaphore`() {
        assertEquals(listOf(Profile, Home, Map, Schedule), destinationsFor(ProfileRole.Professor))
    }

    @Test
    fun `staff has neither semaphore nor timetable`() {
        assertEquals(listOf(Profile, Home, Map), destinationsFor(ProfileRole.Staff))
    }

    @Test
    fun `an unknown role gets only what every role shares`() {
        assertEquals(listOf(Profile, Home, Map), destinationsFor(null))
    }

    @Test
    fun `the profile role is found among permissions, wherever it is`() {
        assertEquals(ProfileRole.Staff, ProfileRole.of(listOf("ROLE_ADMIN", "ROLE_STAFF")))
    }

    @Test
    fun `permissions alone are no profile role`() {
        assertEquals(null, ProfileRole.of(listOf("ROLE_ADMIN", "ROLE_RECEPTION")))
    }
}
