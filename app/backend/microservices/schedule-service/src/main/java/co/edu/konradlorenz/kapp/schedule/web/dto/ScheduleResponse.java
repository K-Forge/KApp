package co.edu.konradlorenz.kapp.schedule.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Wire shape of {@code Schedule}. Built only by {@code mapper.ScheduleMapper}.
 *
 * <p>{@code programCode}, {@code pensumCode} and {@code level} are null on a professor's timetable,
 * and are written as {@code null} rather than left out: the contract makes them required.
 */
public record ScheduleResponse(
        String userId,
        String period,
        String programCode,
        String pensumCode,
        Integer level,
        boolean active,
        String source,
        Instant readAt,
        List<SectionResponse> sections
) {
}
