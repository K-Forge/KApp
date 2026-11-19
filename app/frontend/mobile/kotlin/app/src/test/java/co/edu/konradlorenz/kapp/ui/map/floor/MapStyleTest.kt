package co.edu.konradlorenz.kapp.ui.map.floor

import co.edu.konradlorenz.kapp.data.map.FloorSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** The app's copy of the shared map style: it reads, it matches the original, and it falls back. */
class MapStyleTest {

    private val copy = File("src/main/assets/map-style.json")
    private val style = MapStyle.parse(copy.readText())

    @Test
    fun `the copy is the shared style, byte for byte`() {
        // Tests run from the module; the shared style is at the root of the repository.
        val shared = File("../../../../../docs/design/map-style.json")
        assumeTrue("docs/design/map-style.json is not on this branch yet", shared.exists())
        assertEquals(shared.readText(), copy.readText())
    }

    @Test
    fun `the layers go from the floor up to the selection`() {
        assertEquals(
            listOf("floor", "spaces", "corridors", "doors", "outline", "icons", "labels", "selection"),
            style.layers,
        )
    }

    @Test
    fun `every category and every colour in the style is a colour`() {
        val categories = listOf("TEACHING", "PUBLIC_SERVICE", "OFFICE", "SOCIAL", "FACILITIES", "CIRCULATION", "OTHER")
        assertEquals(categories.toSet(), style.categories.keys)
        for (dark in listOf(false, true)) {
            categories.forEach { assertNotNull(it, argbOf(style.categoryColor(it, dark))) }
            listOf(
                style.floor.pick(dark).fill, style.floor.pick(dark).background, style.outline.color(dark),
                style.spaces.stroke(dark), style.doors.color(dark), style.corridors.color(dark),
                style.labels.colors(dark).color, style.labels.colors(dark).halo,
                style.icons.colors(dark).fill, style.icons.colors(dark).glyph,
            ).forEach { assertNotNull(it, argbOf(it)) }
        }
    }

    @Test
    fun `a category the client does not know, or none, is drawn as OTHER`() {
        val other = style.categoryColor("OTHER", dark = false)
        assertEquals(other, style.categoryColor("ROOFTOP_GARDEN", dark = false))
        assertEquals(other, style.categoryColor(null, dark = false))
        assertEquals(style.categoryColor("OTHER", dark = true), style.categoryColor(null, dark = true))
    }

    @Test
    fun `light and dark are different colours`() {
        assertEquals(false, style.categoryColor("TEACHING", false) == style.categoryColor("TEACHING", true))
    }

    @Test
    fun `labels rank types with an icon first, then by category, unknown ones as OTHER`() {
        val elevator = space(typeCode = "ELEVATOR", category = "CIRCULATION")
        val classroom = space(typeCode = "CLASSROOM", category = "TEACHING")
        val office = space(typeCode = "OFFICE", category = "OFFICE")
        val unknown = space(typeCode = "ROOFTOP", category = "ROOFTOP_GARDEN")
        val ranks = listOf(elevator, classroom, office, unknown).map(style::labelRank)
        assertEquals(ranks.sorted(), ranks)
        assertEquals(style.labelRank(space(typeCode = "X", category = "OTHER")), style.labelRank(unknown))
    }

    @Test
    fun `argbOf reads #RRGGBB and refuses anything else`() {
        assertEquals(0xFFD51A65, argbOf("#D51A65"))
        assertEquals(null, argbOf("#FFF"))
        assertEquals(null, argbOf("pink"))
    }

    private fun space(typeCode: String, category: String?) =
        FloorSpace(code = "x", name = "x", typeCode = typeCode, category = category)
}
