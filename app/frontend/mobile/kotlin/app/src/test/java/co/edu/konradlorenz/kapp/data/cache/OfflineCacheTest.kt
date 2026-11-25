package co.edu.konradlorenz.kapp.data.cache

import co.edu.konradlorenz.kapp.data.crypto.BlobCipher
import co.edu.konradlorenz.kapp.data.crypto.newTestKey
import co.edu.konradlorenz.kapp.data.crypto.testCipher
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inicio's offline copies: sealed on disk, and dropped when they no longer open. */
class OfflineCacheTest {

    @Serializable
    private data class Note(val owner: String, val text: String)

    private val dir: File = Files.createTempDirectory("offline").toFile()
    private val key = newTestKey()
    private val note = Note(owner = "Laura Gómez", text = "Cálculo II, salón 612")

    private fun cache(cipher: BlobCipher = testCipher(key)) = OfflineCache(dir, cipher, now = { 1_700_000_000_000 })

    @After
    fun deleteDir() {
        dir.deleteRecursively()
    }

    @Test
    fun `a saved copy reads back with when it was saved`() {
        cache().save("note", Note.serializer(), note)
        assertEquals(Cached(note, savedAt = 1_700_000_000_000), cache().read("note", Note.serializer()))
    }

    @Test
    fun `nothing of the copy is readable on disk`() {
        cache().save("note", Note.serializer(), note)

        val files = dir.listFiles().orEmpty()
        assertEquals(1, files.size)
        val onDisk = files.single().readBytes().decodeToString()
        assertFalse(onDisk.contains("Laura"))
        assertFalse(onDisk.contains("612"))
        assertFalse(onDisk.contains("savedAt"))
    }

    @Test
    fun `a copy sealed with a lost key reads as none and is deleted`() {
        cache().save("note", Note.serializer(), note)

        // A reinstall: the files survive a backup restore, the Keystore key does not.
        assertNull(cache(testCipher()).read("note", Note.serializer()))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a damaged copy reads as none and is deleted`() {
        cache().save("note", Note.serializer(), note)
        dir.listFiles().orEmpty().single().writeBytes(byteArrayOf(1, 2, 3))

        assertNull(cache().read("note", Note.serializer()))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a copy of a model that changed reads as none`() {
        cache().save("note", Note.serializer(), note)
        assertNull(cache().read("note", Int.serializer()))
    }

    @Test
    fun `a copy that cannot be sealed is not written, and the older one goes`() {
        cache().save("note", Note.serializer(), note)
        val broken = object : BlobCipher {
            override fun seal(plain: ByteArray): ByteArray = error("No key")
            override fun open(sealed: ByteArray): ByteArray = error("No key")
        }

        cache(broken).save("note", Note.serializer(), note.copy(text = "otro"))

        assertNull(cache().read("note", Note.serializer()))
    }

    @Test
    fun `the plain JSON an older build left is deleted, never read`() {
        dir.mkdirs()
        val plain = File(dir, "note.json").apply { writeText("""{"savedAt":1,"value":{"owner":"x","text":"y"}}""") }

        assertNull(cache().read("note", Note.serializer()))
        assertFalse(plain.exists())
    }

    @Test
    fun `clearing deletes every copy, as signing out does`() {
        cache().save("note", Note.serializer(), note)
        cache().save("other", Note.serializer(), note)

        cache().clear()

        assertFalse(dir.exists())
        assertNull(cache().read("note", Note.serializer()))
    }

    @Test
    fun `nothing saved reads as none`() {
        assertNull(cache().read("note", Note.serializer()))
    }
}
