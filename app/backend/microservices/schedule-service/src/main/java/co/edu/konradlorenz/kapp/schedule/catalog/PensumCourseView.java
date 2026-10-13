package co.edu.konradlorenz.kapp.schedule.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of {@code semaphore-service}'s {@code PensumCourse} this service needs, from
 * {@code GET /api/catalog/pensums/{pensumCode}/courses} ({@code docs/api/semaphore.openapi.yaml}):
 * which item of the pensum a course of the timetable is.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is required, not decorative: the real schema
 * carries fields this client has no use for, and without it semaphore-service adding one would break
 * every lookup here.
 *
 * @param sinuCode the course code as SINU carries it, which a timetable's section carries too; null
 *                 where the real one is not known
 * @param code     the printed code semaphore 1.0 still answers with, read while it does
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PensumCourseView(
        String pensumItemCode,
        String sinuCode,
        String code,
        String name
) {
}
