package co.edu.konradlorenz.kapp.schedule.web.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Wire shape of {@code WeekAgenda}. All seven {@link DayOfWeek} keys are always present - built
 * from an {@link java.util.EnumMap} filled with every day, in {@code service.ScheduleService}, so a
 * day with no classes still serialises as {@code []} rather than being missing.
 */
public record WeekAgendaResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        Map<DayOfWeek, List<ClassOccurrenceResponse>> days
) {
}
