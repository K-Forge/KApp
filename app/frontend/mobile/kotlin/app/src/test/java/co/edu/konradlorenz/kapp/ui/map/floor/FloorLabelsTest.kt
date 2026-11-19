package co.edu.konradlorenz.kapp.ui.map.floor

import co.edu.konradlorenz.kapp.data.map.FloorSpace
import co.edu.konradlorenz.kapp.data.map.MapBounds
import co.edu.konradlorenz.kapp.data.map.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The label rules of docs/design/MAP-STYLE.md. */
class FloorLabelsTest {

    @Test
    fun `the door code comes before the name, and the code is never shown`() {
        assertEquals("503-S", FloorLabels.text(space(doorCode = "503-S", name = "Aula")))
        assertEquals("Laboratorio de Interactividad", FloorLabels.text(space(name = "Laboratorio de Interactividad")))
        assertEquals("Aula", FloorLabels.text(space(doorCode = " ", name = "Aula")))
        assertNull(FloorLabels.text(space(name = "")))
    }

    @Test
    fun `a space nobody has identified yet has no label, unless its door has a number`() {
        assertNull(FloorLabels.text(space(name = "Sin identificar", category = "OTHER")))
        assertEquals("214", FloorLabels.text(space(name = "Sin identificar", doorCode = "214", category = "OTHER")))
        assertEquals("Escalera por identificar", FloorLabels.text(space(name = "Escalera por identificar", category = "CIRCULATION")))
    }

    @Test
    fun `a label not wholly on screen is hidden rather than cut at the edge`() {
        val screen = LabelBox(0f, 0f, 1080f, 1920f)
        val inside = candidate("inside", LabelBox.centredOn(540f, 960f, 200f, 40f))
        val cut = candidate("cut", LabelBox.centredOn(1050f, 960f, 200f, 40f))
        assertEquals(listOf("inside"), FloorLabels.place(listOf(inside, cut), view = screen).map { it.item })
        assertEquals(2, FloorLabels.place(listOf(inside, cut)).size)
    }

    @Test
    fun `the anchor is the label point, else the centre of the bounds, else of the shape`() {
        val box = MapBounds(80, 80, 160, 80)
        assertEquals(160f to 120f, FloorLabels.anchor(space(bounds = box)))
        assertEquals(100f to 90f, FloorLabels.anchor(space(bounds = box, labelPoint = MapPoint(100, 90))))
        val shape = listOf(MapPoint(0, 0), MapPoint(10, 0), MapPoint(10, 20), MapPoint(0, 20))
        assertEquals(5f to 10f, FloorLabels.anchor(space(shape = shape)))
        assertNull(FloorLabels.anchor(space()))
    }

    @Test
    fun `the text grows with zoom between the style's sizes`() {
        val style = LabelsStyle(LabelColors("#000000", "#FFFFFF"), LabelColors("#FFFFFF", "#000000"), 10f, 14f)
        assertEquals(10f, FloorLabels.sizeSp(1f, style), 0.001f)
        assertEquals(12f, FloorLabels.sizeSp(2.5f, style), 0.001f)
        assertEquals(14f, FloorLabels.sizeSp(8f, style), 0.001f)
    }

    @Test
    fun `a label wider than its space is hidden`() {
        val wide = candidate("wide", LabelBox.centredOn(50f, 50f, 120f, 20f), spaceWidth = 100f)
        val narrow = candidate("narrow", LabelBox.centredOn(300f, 50f, 80f, 20f), spaceWidth = 100f)
        assertEquals(listOf("narrow"), FloorLabels.place(listOf(wide, narrow)).map { it.item })
        assertEquals(2, FloorLabels.place(listOf(wide, narrow), hideWhenWiderThanSpace = false).size)
    }

    @Test
    fun `on a collision the label earlier in the priority wins, whatever the order it came in`() {
        val office = candidate("office", LabelBox.centredOn(50f, 50f, 60f, 20f), rank = 4)
        val elevator = candidate("elevator", LabelBox.centredOn(60f, 55f, 60f, 20f), rank = 0)
        val apart = candidate("apart", LabelBox.centredOn(400f, 400f, 60f, 20f), rank = 7)
        assertEquals(listOf("elevator", "apart"), FloorLabels.place(listOf(office, elevator, apart)).map { it.item })
    }

    @Test
    fun `an icon stays when its label is wider than its space, and is not held to that width`() {
        val icon = LabelBox.centredOn(50f, 50f, 40f, 40f)
        val lift = candidate("lift", FloorLabels.below(icon, 200f, 20f, 4f), spaceWidth = 30f, icon = icon)
        val placed = FloorLabels.place(listOf(lift)).single()
        assertEquals(icon, placed.icon)
        assertNull(placed.box)
    }

    @Test
    fun `an icon and its label keep other marks off both`() {
        val icon = LabelBox.centredOn(100f, 100f, 40f, 40f)
        val lift = candidate("lift", FloorLabels.below(icon, 80f, 20f, 4f), icon = icon, rank = 0)
        val onIcon = candidate("on icon", LabelBox.centredOn(100f, 100f, 30f, 10f), rank = 1)
        val onLabel = candidate("on label", LabelBox.centredOn(100f, 135f, 30f, 10f), rank = 1)
        val apart = candidate("apart", LabelBox.centredOn(400f, 400f, 30f, 10f), rank = 1)
        assertEquals(
            listOf("lift", "apart"),
            FloorLabels.place(listOf(onIcon, onLabel, apart, lift)).map { it.item },
        )
    }

    @Test
    fun `an icon cut by the screen edge is hidden with its label`() {
        val screen = LabelBox(0f, 0f, 1080f, 1920f)
        val icon = LabelBox.centredOn(1075f, 960f, 40f, 40f)
        val lift = candidate("lift", FloorLabels.below(icon, 20f, 20f, 4f), icon = icon)
        assertEquals(emptyList<String>(), FloorLabels.place(listOf(lift), view = screen).map { it.item })
    }

    @Test
    fun `the label goes centred under its icon`() {
        assertEquals(
            LabelBox(60f, 124f, 140f, 144f),
            FloorLabels.below(LabelBox(80f, 80f, 120f, 120f), 80f, 20f, 4f),
        )
    }

    @Test
    fun `boxes that only touch do not overlap`() {
        assertEquals(false, LabelBox(0f, 0f, 10f, 10f).overlaps(LabelBox(10f, 0f, 20f, 10f)))
        assertEquals(true, LabelBox(0f, 0f, 10f, 10f).overlaps(LabelBox(9f, 9f, 20f, 20f)))
    }

    private fun space(
        name: String = "x",
        doorCode: String? = null,
        bounds: MapBounds? = null,
        shape: List<MapPoint> = emptyList(),
        labelPoint: MapPoint? = null,
        category: String? = null,
    ) = FloorSpace(
        code = "GENERATED-01", name = name, typeCode = "OFFICE", doorCode = doorCode, category = category,
        bounds = bounds, shape = shape, labelPoint = labelPoint,
    )

    private fun candidate(
        item: String,
        box: LabelBox,
        spaceWidth: Float = 1000f,
        rank: Int = 0,
        icon: LabelBox? = null,
    ) = LabelCandidate(item, box, spaceWidth, rank, icon)
}
