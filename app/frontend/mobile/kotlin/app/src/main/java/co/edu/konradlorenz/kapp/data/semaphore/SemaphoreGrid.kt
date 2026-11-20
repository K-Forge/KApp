package co.edu.konradlorenz.kapp.data.semaphore

/**
 * What a semáforo item is painted as: SINU's five statuses, with `PENDING` split into the two the
 * traffic light is named for. *Blocked* is not a status SINU has; it is a `PENDING` item missing
 * from `GET /api/semaphore/me/eligible` (semaphore.openapi.yaml, "BLOCKED is not a status").
 */
enum class CourseStatus {
    Passed,
    InProgress,

    /** `PENDING`, with every prerequisite passed: it can be taken next. */
    Eligible,

    /** `PENDING`, with a prerequisite still to pass. */
    Blocked,

    Failed,

    /** *Aplazada*. New in 2.0.0. */
    Postponed,

    /** A status this build does not know. Shown through `sinuStatus`, as the contract asks. */
    Other,
    ;

    /**
     * Whether a plan may move it: only what is still ahead. Passed and current courses stay, and a
     * failed one goes to the next period by itself (#232), so there is nothing to choose.
     */
    val plannable: Boolean
        get() = this != Passed && this != InProgress && this != Failed

    companion object {
        /**
         * The status of [entry], which is `null` for an item SINU has no record of - the contract
         * says those come back `PENDING`, and a slice that left one out means the same.
         */
        fun of(entry: StudentProgressCourse?, eligible: Boolean): CourseStatus =
            when (entry?.status ?: "PENDING") {
                "PASSED" -> Passed
                "IN_PROGRESS" -> InProgress
                "FAILED" -> Failed
                "POSTPONED" -> Postponed
                "PENDING" -> if (eligible) Eligible else Blocked
                else -> Other
            }
    }
}

/** An elective chosen in a plan for a slot, and whether this semester's bank offers it. */
data class ChosenElective(val sinuCode: String, val offered: Boolean?)

/** One item as drawn: the pensum's, with the student's status and the plan's position over it. */
data class SemaphoreItem(
    val course: PensumCourse,
    val entry: StudentProgressCourse?,
    val status: CourseStatus,
    /** The level it is drawn at: the plan's, or the pensum's when the plan did not move it. */
    val level: Int,
    /** The pensum's level when the plan moved it elsewhere, so the screen can say where from. */
    val movedFrom: Int?,
    val chosenElective: ChosenElective?,
    /** The `#RRGGBB` of its knowledge area, which the screen paints on its edge. */
    val areaColor: String? = null,
) {
    /** The code to show: the course that filled a slot, else the item's own. Never `pensumItemCode`. */
    val displayCode: String?
        get() = entry?.resolvedSinuCode ?: course.sinuCode

    /** The name to show: the course that filled a slot, else the item's own. */
    val displayName: String
        get() = entry?.resolvedName ?: course.name
}

/** One semester of the grid. */
data class SemaphoreLevel(val level: Int, val items: List<SemaphoreItem>) {
    val credits: Int
        get() = items.sumOf { it.course.credits }
}

/**
 * The semáforo as levels, out of the four things it is drawn from.
 *
 * The pensum decides what is on it: an entry of [semaphore] for an item the pensum lacks is left
 * out, and an item with no entry is `PENDING`. Inside a level, items keep the pensum's order.
 *
 * With a [plan], an item it moved is drawn at its planned level - which can be past the last one
 * of the pensum, so the levels run to whichever is further. Without one, this is the *Semáforo
 * Original*.
 *
 * A failed course is drawn in the next period ([studentPeriod]), with or without a plan and
 * whatever placement it had: it must be taken again then, and the API accepts no other level for
 * it (#232, #253).
 */
fun buildSemaphore(
    pensum: Pensum,
    semaphore: StudentSemaphore,
    eligible: Set<String>,
    plan: AcademicPlan?,
): List<SemaphoreLevel> {
    val entries = semaphore.courses.associateBy { it.pensumItemCode }
    val placements = plan?.placements.orEmpty().associateBy { it.pensumItemCode }
    val areaColors = pensum.areas.associate { it.code to it.color }
    val repeatAt = minOf(studentPeriod(pensum, semaphore).next, MAX_PLANNED_LEVEL)

    val items = pensum.courses.map { course ->
        val entry = entries[course.pensumItemCode]
        val status = CourseStatus.of(entry, course.pensumItemCode in eligible)
        // A plan moves only what is still ahead. A placement left on a course SINU has since
        // passed is ignored rather than drawing a passed course in a future semester.
        val placement = placements[course.pensumItemCode]?.takeIf { status.plannable }
        val level = when {
            status == CourseStatus.Failed -> repeatAt
            else -> placement?.plannedLevel ?: course.level
        }
        SemaphoreItem(
            course = course,
            entry = entry,
            status = status,
            level = level,
            movedFrom = course.level.takeIf { it != level },
            chosenElective = placement?.electiveSinuCode?.let {
                ChosenElective(it, placement.electiveOffered)
            },
            areaColor = areaColors[course.area],
        )
    }

    val lastLevel = maxOf(pensum.levels, items.maxOfOrNull { it.level } ?: 0)
    return (1..lastLevel).map { level -> SemaphoreLevel(level, items.filter { it.level == level }) }
}

/** The furthest level semaphore 2.0 accepts in a placement: `plannedLevel` runs 1 to 12. */
const val MAX_PLANNED_LEVEL = 12

/**
 * The levels a plan can put a course at (issue #176): the pensum's, and one after the furthest
 * level already used - the pensum's last, or a level the plan has already gone past it to - so a
 * plan can stretch past the nominal length one semester at a time, up to [MAX_PLANNED_LEVEL].
 */
fun plannableLevels(pensumLevels: Int, plan: AcademicPlan?): IntRange {
    val furthest = maxOf(pensumLevels, plan?.placements?.maxOfOrNull { it.plannedLevel } ?: 0)
    return 1..minOf(MAX_PLANNED_LEVEL, furthest + 1)
}

/**
 * Where the student stands, by the rule the API enforces on plans (#253):
 *
 * - [current] is the highest pensum level with a course `IN_PROGRESS`; with none in progress, the
 *   level after the highest one with a course `PASSED`, or 1 if nothing is passed.
 * - [next] is `current + 1` while a course is in progress, or [current] itself when none is.
 *
 * For the test students, level 5 is in progress, so 31506 (level 4, failed) goes to level 6.
 */
data class StudentPeriod(val current: Int, val next: Int)

fun studentPeriod(pensum: Pensum, semaphore: StudentSemaphore): StudentPeriod {
    val statuses = semaphore.courses.associate { it.pensumItemCode to it.status }
    fun highest(status: String) =
        pensum.courses.filter { statuses[it.pensumItemCode] == status }.maxOfOrNull { it.level }

    val inProgress = highest("IN_PROGRESS")
    if (inProgress != null) return StudentPeriod(current = inProgress, next = inProgress + 1)
    val current = (highest("PASSED") ?: 0) + 1
    return StudentPeriod(current = current, next = current)
}

/**
 * The levels [status] may be moved to in [plan], for a student at [currentLevel]
 * ([StudentPeriod.current]).
 *
 * Nothing goes into a semester that has already finished: a pending course is offered the current
 * one and the ones after it (#232). Nothing that is not [CourseStatus.plannable] is offered any:
 * a failed course already sits in the next period, which is the only level the API takes for it.
 */
fun allowedLevels(
    status: CourseStatus,
    currentLevel: Int,
    pensumLevels: Int,
    plan: AcademicPlan?,
): List<Int> {
    if (!status.plannable) return emptyList()
    return plannableLevels(pensumLevels, plan).filter { it >= currentLevel }
}

/** `"20191"` as people write it: `"2019-1"`. Anything not in that shape is returned as it came. */
fun formatPeriod(period: String): String =
    if (Regex("""\d{4}[12]""").matches(period)) "${period.take(4)}-${period.last()}" else period

/** A grade on the `0..50` scale as it is read out: 41 is `"4,1"`. */
fun formatGrade(grade: Int): String = "${grade / 10},${grade % 10}"

/** Weekly hours, whole or with a half: `4.0` is `"4"`, `4.5` is `"4,5"`. */
fun formatHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString().replace('.', ',')
