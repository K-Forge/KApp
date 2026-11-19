package co.edu.konradlorenz.kapp.data.map

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The demo build's floors, which scripts/capture-demo-data.sh writes out of map-service's seeds:
 * one per level of every building in Mapa's list, each one a floor the renderer can read, with a
 * label point on every drawn space as map 3.7.0 sends it.
 */
class DemoFloorsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val root = File("src/demo/assets/demo/get/api/map")

    private val buildings: List<Building> = json.decodeFromString(File(root, "buildings.json").readText())

    @Test
    fun `every level of every building has its floor`() {
        buildings.forEach { building ->
            building.floors.forEach { level ->
                val file = File(root, "buildings/${building.code}/floors/${level.code}.json")
                assertTrue(file.path, file.exists())
                assertEquals(file.path, level.code, json.decodeFromString<FloorPlan>(file.readText()).code)
            }
        }
    }

    @Test
    fun `every placed space has bounds and a category, and lies on its floor`() {
        floors().forEach { (path, floor) ->
            floor.placed.forEach { space ->
                val box = space.bounds
                assertNotNull("$path ${space.code}", box)
                assertNotNull("$path ${space.code}", space.category)
                box!!
                assertTrue("$path ${space.code}", box.x >= 0 && box.y >= 0)
                assertTrue("$path ${space.code}", box.x + box.width <= floor.width && box.y + box.height <= floor.height)
            }
        }
    }

    @Test
    fun `every placed space has its label point inside its shape`() {
        floors().forEach { (path, floor) ->
            floor.placed.forEach { space ->
                val point = space.labelPoint
                assertNotNull("$path ${space.code}", point)
                assertTrue("$path ${space.code} $point", inside(point!!, space.shape))
            }
        }
    }

    @Test
    fun `the campus has floors with spaces to draw`() {
        assertTrue(floors().count { (_, floor) -> floor.placed.isNotEmpty() } >= 10)
    }

    /** Even-odd ray casting: whether [point] lies inside the polygon [shape]. */
    private fun inside(point: MapPoint, shape: List<MapPoint>): Boolean {
        var inside = false
        shape.indices.forEach { i ->
            val a = shape[i]
            val b = shape[(i + shape.size - 1) % shape.size]
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x).toDouble() * (point.y - a.y) / (b.y - a.y) + a.x
            ) {
                inside = !inside
            }
        }
        return inside
    }

    private fun floors() = File(root, "buildings").walk().filter { it.extension == "json" }
        .map { it.path to json.decodeFromString<FloorPlan>(it.readText()) }
        .toList()
}
