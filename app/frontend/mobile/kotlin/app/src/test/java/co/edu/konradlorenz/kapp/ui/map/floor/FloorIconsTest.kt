package co.edu.konradlorenz.kapp.ui.map.floor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Every icon the shared style names is one the app can draw. */
class FloorIconsTest {

    private val style = MapStyle.parse(File("src/main/assets/map-style.json").readText())

    @Test
    fun `every icon in typeIcons has a drawable`() {
        style.typeIcons.forEach { (type, icon) -> assertNotNull("$type ${icon.material}", FloorIcons.drawable(icon.material)) }
        assertEquals(style.typeIcons.keys, FloorIcons.forTypes(style).keys)
    }

    @Test
    fun `an icon the app does not have draws nothing`() {
        assertNull(FloorIcons.drawable("rocket_launch"))
        val unknown = style.copy(typeIcons = mapOf("LAUNCH_PAD" to TypeIcon("rocket_launch", "airplane")))
        assertEquals(emptyMap<String, Int>(), FloorIcons.forTypes(unknown))
    }
}
