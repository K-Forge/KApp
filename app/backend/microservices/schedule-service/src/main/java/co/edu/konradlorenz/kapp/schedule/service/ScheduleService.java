package co.edu.konradlorenz.kapp.schedule.service;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.schedule.catalog.PensumCatalogService;
import co.edu.konradlorenz.kapp.schedule.color.CourseColors;
import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingResolution;
import co.edu.konradlorenz.kapp.schedule.domain.Schedule;
import co.edu.konradlorenz.kapp.schedule.domain.Section;
import co.edu.konradlorenz.kapp.schedule.map.SedeBuildings;
import co.edu.konradlorenz.kapp.schedule.mapper.ScheduleMapper;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuSection;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetable;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetablePort;
import co.edu.konradlorenz.kapp.schedule.web.dto.ClassOccurrenceResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.SchedulePeriodSummaryResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.WeekAgendaResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A person's timetable as SINU has it, with what KApp adds, and the day and week resolution that is
 * the whole reason the nested model exists. Read-only: nothing here writes, and nothing is stored.
 *
 * <p>KApp adds three things to each section, all of them failing open: the pensum item of the course
 * (a student's only, from semaphore-service), the building of its sede (from map-service) and its
 * colour.
 */
@Service
public class ScheduleService {

    private final SinuTimetablePort sinu;
    private final TimetableReader reader;
    private final PensumCatalogService catalog;
    private final SedeBuildings sedes;
    private final Clock clock;

    public ScheduleService(SinuTimetablePort sinu, TimetableReader reader, PensumCatalogService catalog,
                           SedeBuildings sedes, Clock clock) {
        this.sinu = sinu;
        this.reader = reader;
        this.catalog = catalog;
        this.sedes = sedes;
        this.clock = clock;
    }

    /** @param period null for the current one */
    public Schedule schedule(SinuPerson person, String period) {
        AcademicPeriod wanted = period == null ? current() : AcademicPeriod.parse(period);
        return read(person, wanted)
                .orElseThrow(() -> new ResourceNotFoundException("No schedule found for period " + wanted));
    }

    /** One row per period SINU has a timetable for, newest first. */
    public List<SchedulePeriodSummaryResponse> periods(SinuPerson person) {
        AcademicPeriod current = current();
        return sinu.periods(person).stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .flatMap(p -> reader.read(person, p).stream()
                        .map(r -> new SchedulePeriodSummaryResponse(p.toString(), p.equals(current),
                                r.timetable().sections().size())))
                .toList();
    }

    /** The classes that take place on that date, in the timetable of the period it falls in. */
    public List<ClassOccurrenceResponse> day(SinuPerson person, LocalDate date) {
        return occurrencesOn(timetableOn(person, date), date);
    }

    /** The week of that date, today's when there is none, Monday to Sunday. */
    public WeekAgendaResponse week(SinuPerson person, LocalDate anyDateInWeek) {
        LocalDate monday = (anyDateInWeek == null ? LocalDate.now(clock) : anyDateInWeek).with(DayOfWeek.MONDAY);
        Schedule schedule = timetableOn(person, monday);

        Map<DayOfWeek, List<ClassOccurrenceResponse>> days = new EnumMap<>(DayOfWeek.class);
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = monday.plusDays(offset);
            days.put(date.getDayOfWeek(), occurrencesOn(schedule, date));
        }
        return new WeekAgendaResponse(monday, monday.plusDays(6), days);
    }

    private AcademicPeriod current() {
        return sinu.periodOn(LocalDate.now(clock));
    }

    private Schedule timetableOn(SinuPerson person, LocalDate date) {
        return read(person, sinu.periodOn(date))
                .orElseThrow(() -> new ResourceNotFoundException("No active schedule found"));
    }

    private Optional<Schedule> read(SinuPerson person, AcademicPeriod period) {
        AcademicPeriod current = current();
        return reader.read(person, period).map(reading -> {
            SinuTimetable timetable = reading.timetable();
            Map<String, String> buildings = sedes.bySede();
            Map<String, String> colors = CourseColors.assign(
                    timetable.sections().stream().map(SinuSection::sinuCode).toList());
            List<Section> sections = timetable.sections().stream()
                    .map(s -> new Section(s.sectionCode(), s.sinuCode(),
                            person.role() == SinuPerson.Role.STUDENT
                                    ? catalog.pensumItemCode(timetable.pensumCode(), s.sinuCode()).orElse(null)
                                    : null,
                            s.courseName(), s.level(), s.credits(), s.totalHours(), s.group(), s.subgroup(),
                            s.professor(), s.sede(), s.sede() == null ? null : buildings.get(s.sede().trim()),
                            colors.get(s.sinuCode()), s.meetings()))
                    .toList();
            return new Schedule(person.userId(), period, timetable.programCode(), timetable.pensumCode(),
                    timetable.level(), period.equals(current), sinu.source(), reading.readAt(), sections);
        });
    }

    /**
     * The single place a date is resolved against every meeting of a timetable - used identically by
     * {@link #day} and, once per date of the week, by {@link #week}.
     */
    private List<ClassOccurrenceResponse> occurrencesOn(Schedule schedule, LocalDate date) {
        List<ClassOccurrenceResponse> result = new ArrayList<>();
        for (Section section : schedule.sections()) {
            for (Meeting meeting : section.meetings()) {
                MeetingResolution.periodOn(meeting, date).ifPresent(period ->
                        result.add(ScheduleMapper.toClassOccurrence(section, meeting, period.room())));
            }
        }
        result.sort(Comparator.comparing(ClassOccurrenceResponse::startTime));
        return result;
    }
}
