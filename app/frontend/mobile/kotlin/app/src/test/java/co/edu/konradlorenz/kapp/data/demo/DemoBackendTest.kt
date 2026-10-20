package co.edu.konradlorenz.kapp.data.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which file answers which call in the demo build, and that the file is there: a call the demo
 * cannot answer is a screen that says "No pudimos cargar…" in the class delivery's screenshots.
 */
class DemoBackendTest {

    private fun answer(method: String, path: String, buildingCode: String? = null, prefer: String? = null) =
        DemoBackend.answerFor(method, path, buildingCode, prefer)

    private fun asset(path: String) = DemoAnswer.Asset(path)

    @Test
    fun `a call maps to its method and path`() {
        assertEquals(asset("get/api/semaphore/me/summary.json"), answer("GET", "/api/semaphore/me/summary"))
        assertEquals(asset("post/auth/microsoft.json"), answer("POST", "/auth/microsoft"))
        assertEquals(asset("get/api/catalog/pensums/1015.json"), answer("GET", "/api/catalog/pensums/1015"))
    }

    @Test
    fun `the profile comes from the Prefer header, and a student when there is none`() {
        assertEquals(asset("get/api/users/me.professor.json"), answer("GET", "/api/users/me", prefer = "example=professor"))
        assertEquals(asset("get/api/users/me.staffAdmin.json"), answer("GET", "/api/users/me", prefer = "example=staffAdmin"))
        assertEquals(asset("get/api/users/me.student.json"), answer("GET", "/api/users/me"))
        assertEquals(asset("get/api/users/me.student.json"), answer("GET", "/api/users/me", prefer = "return=minimal"))
        assertEquals(asset("get/api/users/me.student.json"), answer("PATCH", "/api/users/me"))
    }

    @Test
    fun `a room is looked up with its building`() {
        assertEquals(asset("get/api/map/spaces/EC-405.json"), answer("GET", "/api/map/spaces/405", buildingCode = "EC"))
    }

    @Test
    fun `any plan and course answer with the example plan`() {
        val plan = asset("put/api/semaphore/me/plans/any/placements/any.json")
        assertEquals(plan, answer("PUT", "/api/semaphore/me/plans/66f0a1c2/placements/17070"))
        assertEquals(plan, answer("DELETE", "/api/semaphore/me/plans/abc/placements/59085"))
    }

    @Test
    fun `signing out and deleting a plan answer with no body`() {
        assertEquals(DemoAnswer.NoContent, answer("POST", "/auth/logout"))
        assertEquals(DemoAnswer.NoContent, answer("DELETE", "/api/semaphore/me/plans/66f0a1c2"))
    }

    @Test
    fun `every call the screens make has its file`() {
        // Gradle runs unit tests from the module's directory.
        val assets = File("src/demo/assets/demo")
        val calls = listOf(
            answer("POST", "/auth/microsoft"),
            answer("POST", "/auth/refresh"),
            answer("GET", "/api/users/me", prefer = "example=student"),
            answer("GET", "/api/users/me", prefer = "example=professor"),
            answer("GET", "/api/users/me", prefer = "example=staffAdmin"),
            answer("GET", "/api/semaphore/me"),
            answer("GET", "/api/semaphore/me/summary"),
            answer("GET", "/api/semaphore/me/eligible"),
            answer("GET", "/api/semaphore/me/plans"),
            answer("POST", "/api/semaphore/me/plans"),
            answer("GET", "/api/catalog/pensums/1015"),
            answer("GET", "/api/catalog/pensums/1015/electives"),
            answer("GET", "/api/schedule/me"),
            answer("GET", "/api/schedule/me/day"),
            answer("GET", "/api/schedule/me/week"),
            answer("GET", "/api/map/spaces/405", buildingCode = "EC"),
            answer("PUT", "/api/semaphore/me/plans/x/placements/y"),
        )
        calls.filterIsInstance<DemoAnswer.Asset>().forEach {
            assertTrue("missing ${it.path}", File(assets, it.path).isFile)
        }
    }
}
