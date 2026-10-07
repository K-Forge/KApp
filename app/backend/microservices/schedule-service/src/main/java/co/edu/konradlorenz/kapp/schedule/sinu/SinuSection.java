package co.edu.konradlorenz.kapp.schedule.sinu;

import co.edu.konradlorenz.kapp.schedule.domain.Meeting;

import java.util.List;

/**
 * One course and group of a timetable exactly as SINU gives it, before KApp adds anything: the
 * report's {@code Cod.}, {@code Asignatura}, {@code Nivel}, {@code Cred.}, {@code Hor.},
 * {@code Grupo}, {@code SubGrupo}, {@code Docente} and {@code Sede}, and the grid's cells.
 */
public record SinuSection(
        String sectionCode,
        String sinuCode,
        String courseName,
        int level,
        int credits,
        int totalHours,
        String group,
        String subgroup,
        String professor,
        String sede,
        List<Meeting> meetings
) {

    public SinuSection {
        meetings = meetings == null ? List.of() : List.copyOf(meetings);
    }
}
