package co.edu.konradlorenz.kapp.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** The sealing the session and the offline copies share. */
class AesGcmCipherTest {

    private val plain = """{"name":"Laura"}""".encodeToByteArray()

    @Test
    fun `what is sealed opens again with the same key`() {
        val cipher = testCipher()
        assertArrayEquals(plain, cipher.open(cipher.seal(plain)))
    }

    @Test
    fun `each seal has its own IV, so the same bytes never look the same twice`() {
        val cipher = testCipher()
        assertFalse(cipher.seal(plain).contentEquals(cipher.seal(plain)))
    }

    @Test
    fun `another key does not open it`() {
        val sealed = testCipher().seal(plain)
        assertThrows(Exception::class.java) { testCipher().open(sealed) }
    }

    @Test
    fun `a changed byte does not open`() {
        val cipher = testCipher()
        val sealed = cipher.seal(plain).also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertThrows(Exception::class.java) { cipher.open(sealed) }
    }

    @Test
    fun `bytes too short to hold an IV do not open`() {
        assertThrows(Exception::class.java) { testCipher().open(ByteArray(4)) }
    }
}
