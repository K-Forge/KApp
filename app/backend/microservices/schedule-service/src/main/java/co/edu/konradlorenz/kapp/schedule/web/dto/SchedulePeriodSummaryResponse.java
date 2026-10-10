package co.edu.konradlorenz.kapp.schedule.web.dto;

/** One row of the period switcher: wire shape of {@code SchedulePeriodSummary}. */
public record SchedulePeriodSummaryResponse(String period, boolean active, int sectionCount) {
}
