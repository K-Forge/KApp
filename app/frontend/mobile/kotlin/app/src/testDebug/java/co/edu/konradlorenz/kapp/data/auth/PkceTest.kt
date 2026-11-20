package co.edu.konradlorenz.kapp.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {

    @Test
    fun `the challenge is the one RFC 7636 gives for its example verifier`() {
        // RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `a verifier is 43 URL-safe characters and never repeats`() {
        val first = Pkce.verifier()
        assertEquals(43, first.length)
        assertTrue(first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(first, Pkce.verifier())
    }
}
