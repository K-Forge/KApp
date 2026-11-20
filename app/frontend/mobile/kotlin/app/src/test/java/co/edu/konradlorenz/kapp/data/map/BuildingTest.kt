package co.edu.konradlorenz.kapp.data.map

import co.edu.konradlorenz.kapp.data.network.KAppApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mapa's list against the demo build's buildings, which are map-service's seeds: the model reads
 * them, they come ordered by code like the API's, and every row has what Mapa draws.
 */
class BuildingTest {

    private val buildings: List<Building> = KAppApi.json.decodeFromString(
        File("src/demo/assets/demo/get/api/map/buildings.json").readText(),
    )

    @Test
    fun `the campus's buildings come ordered by code`() {
        assertEquals(listOf("BI", "CPC1", "CPC2", "EA", "EC", "JAAB", "MU", "RH", "TK"), buildings.map { it.code })
    }

    @Test
    fun `every building has a name, a campus and at least one level`() {
        buildings.forEach {
            assertTrue(it.code, it.name.isNotBlank() && it.campus.isNotBlank())
            assertTrue(it.code, it.floors.isNotEmpty())
        }
    }

    @Test
    fun `the timetable's buildings are on the list, with their levels`() {
        val edificioCentral = buildings.single { it.code == "EC" }
        assertEquals("Edificio Central", edificioCentral.name)
        assertEquals(9, edificioCentral.floors.size)
        assertTrue(buildings.any { it.code == "BI" })
    }

    @Test
    fun `a building nobody has placed has no address`() {
        assertNull(buildings.single { it.code == "RH" }.address)
    }
}
