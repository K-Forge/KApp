package co.edu.konradlorenz.kapp.data.map

import co.edu.konradlorenz.kapp.data.schedule.MapApi
import retrofit2.HttpException
import java.io.IOException

/**
 * The floors the app has read, kept by their `ETag` for as long as the app runs (map 3.6: reads a
 * client can cache). A floor read again is asked with `If-None-Match`: `304` answers with the one
 * already held, and a `200` with the same floor answers with that same object too, so the screen
 * does not redraw, or lose its zoom, for nothing. There is no offline copy: the map is read online.
 */
class FloorRepository(private val map: MapApi) {

    private val held = mutableMapOf<String, Held>()

    suspend fun floor(building: String, floor: String): FloorPlan {
        val key = "$building/$floor"
        val before = held[key]
        val response = map.floor(building, floor, before?.etag)
        if (response.code() == NOT_MODIFIED && before != null) return before.plan
        if (!response.isSuccessful) throw HttpException(response)
        val read = response.body() ?: throw IOException("GET /api/map/buildings/$key answered with no floor")
        val plan = if (before?.plan == read) before.plan else read
        val etag = response.headers()["ETag"]
        if (etag != null) held[key] = Held(etag, plan) else held.remove(key)
        return plan
    }

    private class Held(val etag: String, val plan: FloorPlan)

    private companion object {
        const val NOT_MODIFIED = 304
    }
}
