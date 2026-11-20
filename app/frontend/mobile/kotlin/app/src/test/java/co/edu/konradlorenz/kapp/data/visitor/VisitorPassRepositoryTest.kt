package co.edu.konradlorenz.kapp.data.visitor

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a `409` from the redeem endpoint becomes what Invitación says. */
class VisitorPassRepositoryTest {

    private fun conflict(issue: String) =
        """{"status":409,"error":"Conflict","details":[{"field":"reason","issue":"$issue"}]}"""

    @Test
    fun `the reason in the details tells a used pass from an expired one`() {
        assertEquals(Redemption.AlreadyUsed, VisitorPassRepository.conflictOf(conflict("already-redeemed")))
        assertEquals(Redemption.Expired, VisitorPassRepository.conflictOf(conflict("expired")))
    }

    @Test
    fun `an unknown reason or an unreadable body is a plain failure`() {
        assertEquals(Redemption.Failed, VisitorPassRepository.conflictOf(conflict("something-new")))
        assertEquals(Redemption.Failed, VisitorPassRepository.conflictOf("""{"status":409}"""))
        assertEquals(Redemption.Failed, VisitorPassRepository.conflictOf("<html>"))
        assertEquals(Redemption.Failed, VisitorPassRepository.conflictOf(null))
    }
}
