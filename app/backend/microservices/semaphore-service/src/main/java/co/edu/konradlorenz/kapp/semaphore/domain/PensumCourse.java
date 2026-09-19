package co.edu.konradlorenz.kapp.semaphore.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * One item of a pensum: a fixed course, or an elective slot with no fixed content.
 *
 * <h2>Two identifiers, because an elective slot has no course code</h2>
 * An elective slot is a slot, not a course: it carries credits, hours, an area and a
 * level, but its content is decided by the student years later. It therefore has no
 * institutional {@code code} at all, and {@link #code()} is {@code null} for one. Every
 * item does have a {@link #pensumItemCode()}, which is what the API addresses items by
 * wherever a slot might appear.
 *
 * <h2>totalHours is derived, never stored</h2>
 * {@code totalHours == weeklyHours * 16} for the 16-week semester. Storing it would
 * create a second copy of the same fact that a careless edit could desynchronise, so it
 * is computed by {@link #totalHours()} on the way out and validated, not trusted, on the
 * way in. It stays a whole number even where {@code weeklyHours} is not: half an hour a
 * week is eight hours a semester.
 *
 * @param code           institutional course code; {@code null} for an elective slot
 * @param pensumItemCode stable identifier within the pensum; never {@code null}
 * @param name           display name
 * @param level          the level (semester) this item sits in - the grid column
 * @param credits        academic credits awarded
 * @param weeklyHours    contact hours per week; a whole hour or a half, see {@link WeeklyHours}
 * @param area           code of the knowledge area - the grid row
 * @param electiveSlot   true when the student later resolves this to a real course
 * @param prerequisites  codes of courses that must all be PASSED first; never null
 * @param sinuCode       the course code as it appears in the university's SINU system,
 *                       or {@code null} where it has not been confirmed. Only the four
 *                       plans printed with codes carry one; the rest use a generated slug
 *                       as their {@code code} and leave this null. It is what separates a
 *                       real code from one of ours, which is why it is also the only one
 *                       a client shows: an invented code on a student's screen is
 *                       indistinguishable from an institutional one, and changes the day
 *                       the real codes arrive.
 */
public record PensumCourse(
        String code,
        String pensumItemCode,
        String name,
        int level,
        int credits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double weeklyHours,
        String area,
        // @JsonProperty mirrors PensumCourseDto's own alias: the wire and seed JSON
        // both use "isElectiveSlot" (the OpenAPI field name), while the Java field keeps
        // the grammatical "electiveSlot" spelling. V002_SeedIngenieriaDeSistemas reads
        // seed JSON straight into this record, so without the alias it fails to parse.
        @JsonProperty("isElectiveSlot") boolean electiveSlot,
        List<String> prerequisites,
        String sinuCode
) {

    /** Weeks in a Konrad Lorenz semester. */
    public static final int WEEKS_PER_SEMESTER = 16;

    public PensumCourse {
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    /** @return contact hours across the full 16-week semester, always whole */
    public int totalHours() {
        return (int) Math.round(weeklyHours * WEEKS_PER_SEMESTER);
    }

    /**
     * @return the identifier this item is addressed by on the wire: the course code for a
     *         fixed course, the pensum item code for an elective slot, which has none
     */
    public String addressableCode() {
        return code != null ? code : pensumItemCode;
    }
}
