package co.edu.konradlorenz.kapp.ui.home

import androidx.compose.ui.graphics.Color
import co.edu.konradlorenz.kapp.data.network.UserProfile

/**
 * What Inicio has on it.
 *
 * The screen is fed by two services that answer independently, so it is modelled as two states
 * rather than one: docs/design/mobile/EstadosHome.dc.html draws a day that is still loading, or
 * empty, or missing entirely, next to a semester card that is none of those. Folding them together
 * would force the whole screen to wait for the slower of the two, which is the one thing the
 * mockups say not to do.
 *
 * Who is being greeted comes from the profile (ProfileRepository), which is read once per session
 * and usually here before Inicio is: [student] is `null` only in the moment it is not.
 */
data class HomeUiState(
    val student: Student?,
    val day: DayState,
    val semester: SemesterState,
)

/**
 * Who is being greeted.
 *
 * [firstName] goes in the greeting and [initials] in the avatar, which is what the mockup draws in
 * place of a photo. [programme] is `academic.programName`, which only a student has.
 */
data class Student(val firstName: String, val initials: String, val programme: String? = null)

/**
 * Builds the greeting and the avatar out of a full name.
 *
 * Two initials at most: "Pepe Pérez Gómez" is PP, not PPG, because the avatar is a 34 dp circle.
 * A single-word name gives a single letter rather than a doubled one.
 */
fun student(fullName: String): Student {
    val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val firstName = words.firstOrNull().orEmpty()
    val initials = words.take(2).map { it.first().uppercaseChar() }.joinToString("")
    return Student(firstName = firstName, initials = initials)
}

/**
 * The greeting out of a profile. Microsoft's given name can be two words - "Laura Marcela" - and
 * the greeting takes the first; the initials are the first name's and the first surname's, which
 * is what the person would write.
 */
fun UserProfile.toStudent(): Student {
    val first = firstName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val initials = listOf(firstName, lastName)
        .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
        .joinToString("")
    return Student(firstName = first, initials = initials, programme = academic?.programName)
}

/**
 * The student's classes today, as `GET /api/schedule/me/day` answers.
 *
 * The three cases below are the three the contract has: a list, an empty list and a 404. There is
 * no error case here yet, for the same reason the login has none - nothing is calling anything, so
 * there is no failure to render. It arrives with the network layer.
 */
sealed interface DayState {

    /** Waiting on schedule-service. */
    data object Loading : DayState

    /**
     * `day` answered 404: this student has never built a schedule.
     *
     * A task, not an error, which is why the mockup gives it a button and not a warning.
     */
    data object NoSchedule : DayState

    /** `day` answered `[]`: there is a schedule, and nothing on it today. */
    data object NoClassesToday : DayState

    /**
     * `day` answered with classes. [next] is the first one still to come and [later] is the rest,
     * in the order the server sorted them, which is by `startTime` ascending.
     */
    data class Classes(val next: NextClass, val later: List<UpcomingClass>) : DayState
}

/**
 * The class at the top of the screen, on the card that carries the "Como llegar" button.
 *
 * [startsInMinutes] is null when the class has already begun. The mockup does not draw that case,
 * so nothing is invented for it: the badge simply goes away.
 */
data class NextClass(
    val courseName: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val building: String,
    val color: Color,
    val startsInMinutes: Int?,
)

/** One of the later rows under "Resto del dia". */
data class UpcomingClass(
    val courseName: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val building: String,
    val color: Color,
)

/**
 * Progress through the pensum, as `GET /api/semaphore/me/summary` answers.
 *
 * Credits, not courses: the contract counts credits and the legend under the bar says "aprobados"
 * about credits. [coursesInProgress] is the one count that is about courses, and it is the one the
 * summary does not carry - see the note on [SemesterState.Ready.coursesInProgress].
 */
sealed interface SemesterState {

    /** Waiting on semaphore-service. */
    data object Loading : SemesterState

    /**
     * It could not be read. No mockup draws this, so the card says it in one line rather than
     * leaving a skeleton that never resolves.
     */
    data object Unavailable : SemesterState

    data class Ready(
        /** `currentLevel`: the semester the student is counted as being in. */
        val level: Int,
        /**
         * Courses being taken right now. `ProgressSummary` counts credits and not courses, so this
         * one is counted off `GET /api/semaphore/me`, which the Semáforo tab reads anyway.
         */
        val coursesInProgress: Int,
        val creditsPassed: Int,
        val creditsInProgress: Int,
        val creditsRemaining: Int,
        val totalCredits: Int,
        /** `percentComplete`, 0 to 100, one decimal. The card prints it rounded. */
        val percentComplete: Double,
    ) : SemesterState {

        /** Share of the bar painted green. Zero rather than a crash if the pensum has no credits. */
        val passedFraction: Float
            get() = if (totalCredits == 0) 0f else creditsPassed.toFloat() / totalCredits

        /** Share of the bar painted lime, straight after [passedFraction]. */
        val inProgressFraction: Float
            get() = if (totalCredits == 0) 0f else creditsInProgress.toFloat() / totalCredits
    }
}
