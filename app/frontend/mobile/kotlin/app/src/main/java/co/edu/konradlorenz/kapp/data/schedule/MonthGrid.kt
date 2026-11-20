package co.edu.konradlorenz.kapp.data.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Horario's Mes view (issue #177): the month as rows of seven days, Monday first, the way the
 * contract runs its weeks. A day outside [month] is `null`, so a month that starts on a Sunday or
 * runs to six rows lays out like any other.
 */
fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val days = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days.chunked(7).map { row -> row + List(7 - row.size) { null } }
}

/**
 * How many classes each day of [month] has, out of the whole timetable rather than four or five
 * calls to `GET /api/schedule/me/week`. The rule is the contract's: a meeting happens on a date
 * that falls on its `dayOfWeek` and inside one of its `periods`, both ends inclusive; the gaps
 * between periods are the weeks it does not meet. Days with none are left out.
 */
fun classesPerDay(schedule: Schedule, month: YearMonth): Map<LocalDate, Int> {
    val counts = mutableMapOf<LocalDate, Int>()
    for (section in schedule.sections) {
        for (meeting in section.meetings) {
            val weekday = DayOfWeek.valueOf(meeting.dayOfWeek)
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                if (date.dayOfWeek != weekday) continue
                val meets = meeting.periods.any { period ->
                    val from = LocalDate.parse(period.from)
                    val to = LocalDate.parse(period.to)
                    !date.isBefore(from) && !date.isAfter(to)
                }
                if (meets) counts[date] = (counts[date] ?: 0) + 1
            }
        }
    }
    return counts
}
