package co.edu.konradlorenz.kapp.data.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The demo's fixed day against the timetable examples it is answered from: if a recapture moves
 * the example week or its classes, Inicio and Horario would open empty in the delivery's
 * screenshots, and this says so first.
 */
class DemoClockTest {

    private fun read(path: String) =
        Json.parseToJsonElement(File("src/demo/assets/demo/get/api/schedule/me/$path").readText())

    private fun startTimes(classes: kotlinx.serialization.json.JsonElement) =
        classes.jsonArray.map { LocalTime.parse(it.jsonObject.getValue("startTime").jsonPrimitive.content) }

    @Test
    fun `today is in the example week and has classes still ahead`() {
        val week = read("week.json").jsonObject
        assertEquals(
            DemoClock.today.with(DayOfWeek.MONDAY).toString(),
            week.getValue("weekStart").jsonPrimitive.content,
        )
        val today = startTimes(week.getValue("days").jsonObject.getValue(DemoClock.today.dayOfWeek.name))
        assertTrue("no classes on ${DemoClock.today}", today.isNotEmpty())
        assertTrue(today.all { it > DemoClock.now })
    }

    @Test
    fun `the day example has a class still ahead, so Inicio has a next one`() {
        assertTrue(startTimes(read("day.json")).any { it > DemoClock.now })
    }
}
