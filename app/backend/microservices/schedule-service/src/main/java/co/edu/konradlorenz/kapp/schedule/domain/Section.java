package co.edu.konradlorenz.kapp.schedule.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * One course the person takes, or teaches, in one group, with its weekly meetings - what SINU's
 * report lists as an asignatura with its grupo - plus what KApp adds to it.
 *
 * @param sectionCode    SINU's identifier of this course and group in the period: the report's
 *                       {@code Cod.}
 * @param sinuCode       the course code as SINU carries it, the one the semáforo shows too
 * @param pensumItemCode the matching item of the student's pensum; null for a course outside it, and
 *                       always on a professor's timetable
 * @param subgroup       null when the group is not subdivided
 * @param sede           as SINU writes it; in SINU each building is a sede
 * @param buildingCode   the KApp building the sede maps to, for the map; null while nobody has mapped
 *                       the sede in the portal
 * @param color          {@code #RRGGBB}, see {@code color.CourseColors}
 */
public record Section(
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
        String color,
        List<Meeting> meetings
) {

    public Section {
        meetings = meetings == null ? List.of() : List.copyOf(meetings);
    }

    /** First day of the course over the semester: the earliest {@code from} of all its meetings. */
    public LocalDate startDate() {
        return meetings.stream().flatMap(m -> m.periods().stream()).map(MeetingPeriod::from)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    /** Last day of the course over the semester: the latest {@code to} of all its meetings. */
    public LocalDate endDate() {
        return meetings.stream().flatMap(m -> m.periods().stream()).map(MeetingPeriod::to)
                .max(Comparator.naturalOrder()).orElse(null);
    }
}
