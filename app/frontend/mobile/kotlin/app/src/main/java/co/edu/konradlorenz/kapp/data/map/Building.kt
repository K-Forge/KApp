package co.edu.konradlorenz.kapp.data.map

import kotlinx.serialization.Serializable

/**
 * `Building` in docs/api/map.openapi.yaml, as far as Mapa's list reads it: what `GET
 * /api/map/buildings` answers, ordered by code. The code is the one a class's room carries in the
 * timetable - EC 405 is in EC.
 */
@Serializable
data class Building(
    val code: String,
    val name: String,
    val campus: String,
    /** Its street address as people write it. Absent when nobody has said. */
    val address: String? = null,
    val floors: List<BuildingFloor> = emptyList(),
)

/** One level of a building: a basement, a mezzanine and a terrace count as levels too. */
@Serializable
data class BuildingFloor(val code: String, val name: String)
