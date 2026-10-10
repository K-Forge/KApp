package co.edu.konradlorenz.kapp.semaphore.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * One item of a pensum: a fixed course, or an elective slot with no fixed content.
 *
 * <h2>One identifier to address it, one code to show</h2>
 * Every item, fixed course and elective slot alike, is addressed by its
 * {@link #pensumItemCode()}: in path parameters, prerequisites and placements. It is KApp's own,
 * stable, and never shown to a person. What a client shows is {@link #sinuCode()}, the code the
 * university itself uses, present only where it is known: nineteen of the twenty-three published
 * plans print no codes at all, and an invented code on a student's screen would be
 * indistinguishable from an institutional one.
 *
 * <p>Semaphore 1.0 also carried the printed {@code code}. It always equalled the item code, so
 * 2.0 dropped it; documents stored before, and the oldest seed, still have it, which is why
 * unknown properties are ignored when this is read.
 *
 * <h2>totalHours is derived, never stored</h2>
 * {@code totalHours == weeklyHours * 16} for the 16-week semester. Storing it would
 * create a second copy of the same fact that a careless edit could desynchronise, so it
 * is computed by {@link #totalHours()} on the way out and validated, not trusted, on the
 * way in. It stays a whole number even where {@code weeklyHours} is not: half an hour a
 * week is eight hours a semester.
 *
 * @param pensumItemCode stable identifier within the pensum; never {@code null}
 * @param name           display name
 * @param level          the level (semester) this item sits in - the grid column
 * @param credits        academic credits awarded
 * @param weeklyHours    contact hours per week; a whole hour or a half, see {@link WeeklyHours}
 * @param area           code of the knowledge area - the grid row
 * @param electiveSlot   true for a slot the student fills with a course from the elective bank
 * @param prerequisites  pensumItemCodes of the items that must all be PASSED first; never null
 * @param sinuCode       the course code as SINU carries it, or {@code null} where it is not known
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PensumCourse(
        String pensumItemCode,
        String name,
        int level,
        int credits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double weeklyHours,
        String area,
        // @JsonProperty mirrors PensumCourseDto's own alias: the wire and seed JSON
        // both use "isElectiveSlot" (the OpenAPI field name), while the Java field keeps
        // the grammatical "electiveSlot" spelling. The seed change units read seed JSON
        // straight into this record, so without the alias it fails to parse.
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
}
