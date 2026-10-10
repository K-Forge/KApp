package co.edu.konradlorenz.kapp.schedule.domain;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;

import java.time.Instant;
import java.util.List;

/**
 * A person's whole timetable for one academic period, as SINU has it and as this service serves it.
 * Built on every read, never stored.
 *
 * @param userId      the {@code sub} claim of the caller's token
 * @param programCode the student's program; null on a professor's timetable, like pensumCode and level
 * @param active      whether this is the current period: the one served when no period is asked for
 * @param readAt      when it was read from SINU; it is kept a few minutes at most
 */
public record Schedule(
        String userId,
        AcademicPeriod period,
        String programCode,
        String pensumCode,
        Integer level,
        boolean active,
        Source source,
        Instant readAt,
        List<Section> sections
) {

    public Schedule {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
