package co.edu.konradlorenz.kapp.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The photo kept on this phone as Inicio reads it (issue #231): a removal reaches whoever shows the
 * photo, and touches only that account's files. Saving needs Android's Bitmap, so the emulator and
 * the screenshots cover it.
 */
class LocalAvatarTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `removing a photo tells Inicio to read it again`() {
        val avatar = LocalAvatar(folder.root)
        val before = avatar.changes.value

        avatar.remove("ana")

        assertEquals(before + 1, avatar.changes.value)
    }

    @Test
    fun `removing one account's photo leaves the next person's alone`() {
        val avatar = LocalAvatar(folder.root)
        val ana = File(folder.root, "ana-photo.jpg").apply { writeText("x") }
        val luis = File(folder.root, "luis-photo.jpg").apply { writeText("x") }

        avatar.remove("ana")

        assertFalse(ana.exists())
        assertTrue(luis.exists())
    }
}
