package co.edu.konradlorenz.kapp.data.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a floor is fitted to on screen, and which space a tap lands on. */
class FloorPlanTest {

    private fun square(x: Int, y: Int, side: Int) =
        listOf(MapPoint(x, y), MapPoint(x + side, y), MapPoint(x + side, y + side), MapPoint(x, y + side))

    private fun space(shape: List<MapPoint>, code: String = "x") =
        FloorSpace(code = code, name = "x", typeCode = "OTHER", shape = shape)

    @Test
    fun `a tap lands on the space whose shape it is in, not on its box`() {
        // An L: the box's top right corner is outside the shape.
        val l = listOf(MapPoint(0, 0), MapPoint(100, 0), MapPoint(100, 50), MapPoint(50, 50), MapPoint(50, 100), MapPoint(0, 100))
        val floor = FloorPlan("P1", 1.0, "Piso 1", 200, 200, spaces = listOf(space(l, "L")))
        assertEquals("L", floor.spaceAt(25f, 75f)?.code)
        assertEquals(null, floor.spaceAt(75f, 75f))
        assertEquals(null, floor.spaceAt(150f, 10f))
    }

    @Test
    fun `where shapes overlap the smallest wins, and a space not drawn is never hit`() {
        val floor = FloorPlan(
            "P1", 1.0, "Piso 1", 400, 400,
            spaces = listOf(space(square(0, 0, 300), "hall"), space(square(100, 100, 50), "kiosk"), space(emptyList(), "none")),
        )
        assertEquals("kiosk", floor.spaceAt(120f, 120f)?.code)
        assertEquals("hall", floor.spaceAt(20f, 20f)?.code)
    }

    @Test
    fun `without an outline, the drawn part is the box around the placed spaces`() {
        val floor = FloorPlan(
            code = "P2", level = 2.0, name = "Piso 2", width = 2400, height = 2320,
            spaces = listOf(space(square(200, 400, 100)), space(square(2000, 2000, 100)), space(emptyList())),
        )
        assertEquals(MapBounds(200, 400, 1900, 1700), floor.drawnBounds)
    }

    @Test
    fun `a traced outline wins over the spaces`() {
        val floor = FloorPlan(
            code = "P1", level = 1.0, name = "Piso 1", width = 2400, height = 2320,
            outline = square(100, 100, 2000),
            spaces = listOf(space(square(200, 400, 100))),
        )
        assertEquals(MapBounds(100, 100, 2000, 2000), floor.drawnBounds)
    }

    @Test
    fun `the spaces not drawn yet are kept apart, never dropped`() {
        val drawn = space(square(0, 0, 10))
        val notYet = space(emptyList())
        val floor = FloorPlan(code = "P3", level = 3.0, name = "Piso 3", width = 100, height = 100, spaces = listOf(drawn, notYet))
        assertEquals(listOf(drawn), floor.placed)
        assertEquals(listOf(notYet), floor.unplaced)
    }

    @Test
    fun `a floor with nothing drawn is its whole drawing`() {
        val floor = FloorPlan(code = "S1", level = -1.0, name = "Sótano", width = 1525, height = 629)
        assertEquals(MapBounds(0, 0, 1525, 629), floor.drawnBounds)
    }
}
