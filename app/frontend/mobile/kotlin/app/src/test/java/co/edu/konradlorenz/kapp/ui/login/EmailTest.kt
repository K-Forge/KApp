package co.edu.konradlorenz.kapp.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule behind the fixed domain printed inside the e-mail field, described in the "correo" note
 * of docs/design/mobile/canvas.json. It is pure string work, so it can be pinned down now even
 * though nothing sends the address anywhere yet.
 */
class EmailTest {

    @Test
    fun `adds the institutional domain to what the student typed`() {
        assertEquals(
            "pepito.perez@konradlorenz.edu.co",
            institutionalEmail("pepito.perez"),
        )
    }

    @Test
    fun `does not repeat the domain when the whole address is pasted`() {
        assertEquals(
            "pepito.perez@konradlorenz.edu.co",
            institutionalEmail("pepito.perez@konradlorenz.edu.co"),
        )
    }

    @Test
    fun `replaces an outside domain with the institutional one`() {
        assertEquals(
            "pepito.perez@konradlorenz.edu.co",
            institutionalEmail("pepito.perez@gmail.com"),
        )
    }

    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals(
            "pepito.perez@konradlorenz.edu.co",
            institutionalEmail("  pepito.perez  "),
        )
    }

    @Test
    fun `an empty field produces no address, not a bare domain`() {
        assertEquals("", institutionalEmail("   "))
    }
}
