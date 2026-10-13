package co.edu.konradlorenz.kapp.schedule.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Wire shape of {@code Section}. Built only by {@code mapper.ScheduleMapper}.
 *
 * <p>{@code pensumItemCode}, {@code subgroup} and {@code buildingCode} may be null and are always
 * written, so "no subgroup" is never confused with "subgroup unknown".
 */
public record SectionResponse(
        String sectionCode,
        String sinuCode,
        String pensumItemCode,
        String courseName,
        int level,
        int credits,
        int totalHours,
        String group,
        String subgroup,
        String professor,
        String sede,
        String buildingCode,
        LocalDate startDate,
        LocalDate endDate,
        String color,
        List<MeetingResponse> meetings
) {
}
