package co.edu.konradlorenz.kapp.data.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a floor is fitted to on screen. */
class FloorPlanTest {

    private fun square(x: Int, y: Int, side: Int) =
        listOf(MapPoint(x, y), MapPoint(x + side, y), MapPoint(x + side, y + side), MapPoint(x, y + side))

    private fun space(shape: List<MapPoint>) = FloorSpace(code = "x", name = "x", typeCode = "OTHER", shape = shape)

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
    fun `a floor with nothing drawn is its whole drawing`() {
        val floor = FloorPlan(code = "S1", level = -1.0, name = "Sótano", width = 1525, height = 629)
        assertEquals(MapBounds(0, 0, 1525, 629), floor.drawnBounds)
    }
}
