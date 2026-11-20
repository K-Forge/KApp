package co.edu.konradlorenz.kapp.data.map

import co.edu.konradlorenz.kapp.data.schedule.MapApi
import co.edu.konradlorenz.kapp.data.schedule.SpaceDetail
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Reading a floor again by its ETag (map 3.6). */
class FloorRepositoryTest {

    private val p1 = FloorPlan(code = "P1", level = 1.0, name = "Piso 1", width = 100, height = 100, version = 3)

    @Test
    fun `the first read sends no ETag, the next one sends the one it got`() = runBlocking {
        val map = FakeMap(Response.success(p1, headersOf("ETag", "\"v3\"")))
        val floors = FloorRepository(map)
        floors.floor("EC", "P1")
        floors.floor("EC", "P1")
        assertEquals(listOf(null, "\"v3\""), map.sent)
    }

    @Test
    fun `304 answers with the floor already held`() = runBlocking {
        val map = FakeMap(Response.success(p1, headersOf("ETag", "\"v3\"")))
        val floors = FloorRepository(map)
        val first = floors.floor("EC", "P1")
        map.next = notModified()
        assertSame(first, floors.floor("EC", "P1"))
    }

    @Test
    fun `the same floor read again is the same object, so the screen keeps its zoom`() = runBlocking {
        val map = FakeMap(Response.success(p1, headersOf("ETag", "\"v3\"")))
        val floors = FloorRepository(map)
        val first = floors.floor("EC", "P1")
        map.next = Response.success(p1.copy(), headersOf("ETag", "\"v3b\""))
        assertSame(first, floors.floor("EC", "P1"))
    }

    @Test
    fun `a changed floor replaces the one held`() = runBlocking {
        val map = FakeMap(Response.success(p1, headersOf("ETag", "\"v3\"")))
        val floors = FloorRepository(map)
        floors.floor("EC", "P1")
        val redrawn = p1.copy(version = 4, width = 120)
        map.next = Response.success(redrawn, headersOf("ETag", "\"v4\""))
        assertEquals(redrawn, floors.floor("EC", "P1"))
        floors.floor("EC", "P1")
        assertEquals("\"v4\"", map.sent.last())
    }

    @Test
    fun `floors are held apart, and an answer without an ETag is not held`() = runBlocking {
        val map = FakeMap(Response.success(p1))
        val floors = FloorRepository(map)
        floors.floor("EC", "P1")
        floors.floor("EC", "P1")
        floors.floor("EC", "P2")
        assertEquals(listOf<String?>(null, null, null), map.sent)
    }

    @Test
    fun `an error is an error, and keeps nothing`() = runBlocking {
        val map = FakeMap(Response.error(404, "{}".toResponseBody()))
        val failed = runCatching { FloorRepository(map).floor("EC", "P9") }.exceptionOrNull()
        assertTrue(failed is HttpException)
        assertNull(map.sent.single())
    }

    private fun notModified(): Response<FloorPlan> = Response.error(
        "".toResponseBody(),
        okhttp3.Response.Builder()
            .code(304)
            .message("Not Modified")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/api/map/buildings/EC/floors/P1").build())
            .build(),
    )

    private class FakeMap(var next: Response<FloorPlan>) : MapApi {
        val sent = mutableListOf<String?>()

        override suspend fun floor(building: String, floor: String, etag: String?): Response<FloorPlan> {
            sent += etag
            return next
        }

        override suspend fun buildings(): List<Building> = error("not used here")

        override suspend fun space(room: String, buildingCode: String): SpaceDetail = error("not used here")
    }
}
