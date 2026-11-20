package co.edu.konradlorenz.kapp.data.schedule

import co.edu.konradlorenz.kapp.data.network.KAppApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The demo build's rooms, which scripts/capture-demo-data.sh takes from map-service's seeds: each
 * room a class names in the demo's day and week is that room, in that building, and not the
 * contract's example (issue #132: Horario opened 508 and showed 302).
 */
class DemoRoomsTest {

    private val demo = File("src/demo/assets/demo/get/api")

    private val asked: Set<Pair<String, String>> =
        listOf("schedule/me/day.json", "schedule/me/week.json").flatMapTo(mutableSetOf()) { path ->
            rooms(KAppApi.json.parseToJsonElement(File(demo, path).readText()))
        }

    @Test
    fun `every room file is the room its class names`() {
        val files = asked.mapNotNull { (building, room) ->
            File(demo, "map/spaces/$building-$room.json").takeIf { it.exists() }?.let { Triple(building, room, it) }
        }
        assertTrue("the demo timetable has rooms on the map", files.isNotEmpty())
        files.forEach { (building, room, file) ->
            val space = KAppApi.json.decodeFromString<SpaceDetail>(file.readText())
            assertEquals(file.name, room, space.code)
            assertEquals(file.name, building, space.building.code)
        }
    }

    @Test
    fun `two rooms are two different rooms`() {
        val names = File(demo, "map/spaces").listFiles().orEmpty()
            .map { KAppApi.json.decodeFromString<SpaceDetail>(it.readText()).name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `no room file is left over from a room no class names`() {
        File(demo, "map/spaces").listFiles().orEmpty().forEach { file ->
            val (building, room) = file.nameWithoutExtension.split("-", limit = 2)
            assertTrue(file.name, building to room in asked)
        }
    }

    /** Every `buildingCode` and `room` that sit together in one object of [node]. */
    private fun rooms(node: JsonElement): List<Pair<String, String>> = when (node) {
        is JsonObject -> {
            val building = (node["buildingCode"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val room = (node["room"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            listOfNotNull(if (building != null && room != null) building to room else null) +
                node.values.flatMap(::rooms)
        }
        is JsonArray -> node.flatMap(::rooms)
        else -> emptyList()
    }
}
