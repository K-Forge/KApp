package co.edu.konradlorenz.kapp.data.demo

import java.time.LocalDate
import java.time.LocalTime

/**
 * The day and time the demo build lives in, whatever the device's clock says: Monday 21 September
 * 2026 at 10:00, inside the week the contract's timetable example describes. Monday has a class,
 * the one `GET /api/schedule/me/day`'s example answers, and at 10:00 it is still ahead - so Inicio
 * shows it as the next class and Horario opens on a day with classes.
 */
object DemoClock {
    val today: LocalDate = LocalDate.of(2026, 9, 21)
    val now: LocalTime = LocalTime.of(10, 0)
}
