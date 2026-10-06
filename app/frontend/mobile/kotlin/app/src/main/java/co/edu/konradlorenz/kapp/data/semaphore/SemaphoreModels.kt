package co.edu.konradlorenz.kapp.data.semaphore

import kotlinx.serialization.Serializable

// The shapes of docs/api/semaphore.openapi.yaml 2.0.0, as far as the app reads them. Two rules
// from the contract run through all of them: every item is addressed by `pensumItemCode`, which is
// never shown, and the code a person sees is `sinuCode` - or the name, where it is null.

/** `Pensum`: the whole degree plan, which is what the semáforo is drawn from. */
@Serializable
data class Pensum(
    val pensumCode: String,
    val programCode: String,
    val programName: String,
    val totalCredits: Int,
    /** The number of levels (semesters) the plan spans. */
    val levels: Int,
    val areas: List<PensumArea>,
    val courses: List<PensumCourse>,
)

/** `PensumArea`: a knowledge area. [color] tints its items, as `#RRGGBB`. */
@Serializable
data class PensumArea(
    val code: String,
    val name: String,
    val color: String,
    val credits: Int,
)

/** `PensumCourse`: a fixed course or an elective slot. No `code` since 2.0.0. */
@Serializable
data class PensumCourse(
    val pensumItemCode: String,
    val sinuCode: String? = null,
    val name: String,
    val level: Int,
    val credits: Int,
    /** A number, not an integer: four items of the published plans carry a half. */
    val weeklyHours: Double,
    val totalHours: Int,
    val area: String,
    val isElectiveSlot: Boolean,
    /** `pensumItemCode`s that must all be `PASSED` first. */
    val prerequisites: List<String> = emptyList(),
)

/**
 * `StudentSemaphore`: the student's semáforo as SINU has it.
 *
 * [source] is `SINU`, or `TEST` while the university has not opened SINU - in which case the app
 * says the data is not real.
 */
@Serializable
data class StudentSemaphore(
    val programCode: String,
    val pensumCode: String,
    val currentLevel: Int,
    val source: String,
    val readAt: String,
    val courses: List<StudentProgressCourse>,
)

/**
 * `StudentProgressCourse`: one item's status.
 *
 * [status] is kept as the string the API sends rather than an enum, so a value this build does not
 * know - the contract expects SINU to have some - is shown through [sinuStatus] instead of failing
 * the whole semáforo. CourseStatus.of reads it.
 */
@Serializable
data class StudentProgressCourse(
    val pensumItemCode: String,
    val status: String,
    val sinuStatus: String? = null,
    /** `"20262"`: the year and the semester. `null` when not taken yet. */
    val period: String? = null,
    /** `0..50`, read-only, `null` unless SINU gives it. Never asked of the student. */
    val grade: Int? = null,
    /** For an elective slot, the course that filled it. */
    val resolvedSinuCode: String? = null,
    val resolvedName: String? = null,
)

/** `ProgressSummary`: credit totals, recomputed by the server on every call. */
@Serializable
data class ProgressSummary(
    val creditsPassed: Int,
    val creditsInProgress: Int,
    val creditsRemaining: Int,
    val totalCredits: Int,
    val percentComplete: Double,
    val currentLevel: Int,
)

/** `ElectiveOffering`: a course of one semester's elective bank. */
@Serializable
data class ElectiveOffering(
    val sinuCode: String,
    val name: String,
    val credits: Int,
    val weeklyHours: Double,
    /** The slots it fills, by `pensumItemCode`. Empty means any elective slot. */
    val slots: List<String> = emptyList(),
)

/**
 * `AcademicPlan`: the student's own arrangement of the pensum - the one thing they write. Only the
 * courses that moved are in [placements]; everything else sits where the pensum puts it.
 */
@Serializable
data class AcademicPlan(
    val id: String,
    val name: String,
    val pensumCode: String,
    val primary: Boolean,
    val placements: List<Placement>,
)

/** `Placement`: an item at another level, or an elective slot with a course chosen for it. */
@Serializable
data class Placement(
    val pensumItemCode: String,
    val plannedLevel: Int,
    val electiveSinuCode: String? = null,
    /** `false`: the chosen elective is not offered this semester, and the app says so. */
    val electiveOffered: Boolean? = null,
)

/** `AcademicPlanRequest`. */
@Serializable
data class AcademicPlanRequest(val name: String, val pensumCode: String)

/** `PlacementRequest`. [electiveSinuCode] only for an elective slot; `null` chooses none. */
@Serializable
data class PlacementRequest(val plannedLevel: Int, val electiveSinuCode: String? = null)
