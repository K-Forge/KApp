package co.edu.konradlorenz.kapp.schedule.sinu;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;

import java.util.List;

/**
 * A person's timetable for one period as SINU gives it.
 *
 * @param programCode the student's program; null for a professor, like pensumCode and level
 */
public record SinuTimetable(
        AcademicPeriod period,
        String programCode,
        String pensumCode,
        Integer level,
        List<SinuSection> sections
) {

    public SinuTimetable {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
