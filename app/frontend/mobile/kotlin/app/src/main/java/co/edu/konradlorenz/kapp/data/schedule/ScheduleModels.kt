package co.edu.konradlorenz.kapp.data.schedule

import kotlinx.serialization.Serializable

// docs/api/schedule.openapi.yaml 2.0.0: a timetable read from SINU, which nothing here writes.
// `room`, `buildingCode` and `subgroup` are always sent and may be null; a null room is a real
// state - no classroom assigned for that stretch - and is drawn, never hidden.

/**
 * `Schedule`: a person's timetable for one period. [source] is `SINU`, or `TEST` while SINU is not
 * open; [programCode], [pensumCode] and [level] are `null` for a professor.
 */
@Serializable
data class Schedule(
    val period: String,
    val programCode: String? = null,
    val pensumCode: String? = null,
    val level: Int? = null,
    val active: Boolean,
    val source: String,
    val readAt: String,
    val sections: List<Section>,
)

/** `Section`: a course and the group taken, or taught. `Enrollment` before 2.0.0. */
@Serializable
data class Section(
    val sectionCode: String,
    val sinuCode: String,
    val pensumItemCode: String? = null,
    val courseName: String,
    val level: Int,
    val credits: Int,
    val totalHours: Int,
    val group: String,
    val subgroup: String? = null,
    val professor: String,
    val sede: String,
    val buildingCode: String? = null,
    val startDate: String,
    val endDate: String,
    val color: String,
    val meetings: List<Meeting>,
)

/** `Meeting`: a weekly slot, taught over [periods] that need not be contiguous. */
@Serializable
data class Meeting(
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    /** 45-minute blocks, which is how SINU schedules. */
    val blocks: Int,
    val periods: List<MeetingPeriod>,
)

/** `MeetingPeriod`: a date range, both ends inclusive, and the room in force during it. */
@Serializable
data class MeetingPeriod(val from: String, val to: String, val room: String? = null)

/**
 * `ClassOccurrence`: one class on one date, already resolved by the server - the room is the one
 * in force that day. What Inicio and the timetable draw.
 */
@Serializable
data class ClassOccurrence(
    val sectionCode: String,
    val sinuCode: String,
    val pensumItemCode: String? = null,
    val courseName: String,
    val group: String,
    val professor: String,
    val startTime: String,
    val endTime: String,
    val blocks: Int,
    val sede: String,
    val buildingCode: String? = null,
    val room: String? = null,
    val color: String,
)

/** `WeekAgenda`: Monday to Sunday. [days] always carries the seven keys, `MONDAY`..`SUNDAY`. */
@Serializable
data class WeekAgenda(
    val weekStart: String,
    val weekEnd: String,
    val days: Map<String, List<ClassOccurrence>>,
)
