package co.edu.konradlorenz.kapp.schedule.sinu.fake;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Source;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuSection;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetable;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetablePort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * The test SINU: serves the invented timetable of {@code docs/api/sinu/example.json} while the
 * university has not opened the real one, and says so ({@link Source#TEST}).
 *
 * <p>Every student is served the invented student's timetable, and every professor the sections of
 * {@value #PROFESSOR}, who teaches two of them: the same data from both sides. The example is written
 * for 2027-1; it is moved, whole weeks at a time, onto whatever period is current, so the dev server
 * has classes this week and every week of the semester. Weekdays, gaps and rooms are kept.
 *
 * <p>It only has the current period. A student's past periods are in the semáforo, not here.
 */
@Component
@ConditionalOnProperty(name = "kapp.sinu.adapter", havingValue = "fake", matchIfMissing = true)
public class FakeSinuTimetableAdapter implements SinuTimetablePort {

    /** The example's own copy, which a test keeps identical to docs/api/sinu/example.json. */
    static final String EXAMPLE = "sinu/example.json";

    /** The invented professor of the example, who teaches two of its sections. */
    static final String PROFESSOR = "DOCENTE EJEMPLO UNO";

    private final Clock clock;
    private final Example example;

    public FakeSinuTimetableAdapter(Clock clock, ObjectMapper json) {
        this.clock = clock;
        try (InputStream in = new ClassPathResource(EXAMPLE).getInputStream()) {
            this.example = json.readValue(in, Example.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the test SINU's timetable " + EXAMPLE, e);
        }
    }

    @Override
    public Source source() {
        return Source.TEST;
    }

    /** The first half of the year is the first semester, the second half the second. */
    @Override
    public AcademicPeriod periodOn(LocalDate date) {
        return new AcademicPeriod(date.getYear(), date.getMonthValue() <= 6 ? 1 : 2);
    }

    @Override
    public List<AcademicPeriod> periods(SinuPerson person) {
        return List.of(periodOn(LocalDate.now(clock)));
    }

    @Override
    public Optional<SinuTimetable> timetable(SinuPerson person, AcademicPeriod period) {
        if (!period.equals(periodOn(LocalDate.now(clock)))) {
            return Optional.empty();
        }
        long shift = ChronoUnit.DAYS.between(firstMonday(AcademicPeriod.parse(example.timetable().period())),
                firstMonday(period));
        List<SinuSection> sections = example.timetable().sections().stream()
                .filter(s -> person.role() == SinuPerson.Role.STUDENT || PROFESSOR.equals(s.professor()))
                .map(s -> s.toSection(shift))
                .toList();
        boolean student = person.role() == SinuPerson.Role.STUDENT;
        return Optional.of(new SinuTimetable(period,
                student ? example.person().programCode() : null,
                student ? example.person().pensumCode() : null,
                student ? example.person().currentLevel() : null,
                sections));
    }

    /**
     * The Monday classes start on: the first one from 1 February in the first semester, and from 25
     * July in the second. 2026-2 starts on 27 July 2026, as a real 2026-2 timetable does, and 2027-1 on
     * 1 February 2027, as the example does.
     */
    static LocalDate firstMonday(AcademicPeriod period) {
        LocalDate from = period.semester() == 1
                ? LocalDate.of(period.year(), 2, 1)
                : LocalDate.of(period.year(), 7, 25);
        return from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    // ---------------------------------------------------- the shape of docs/api/sinu/example.json

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Example(Person person, Timetable timetable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(String programCode, String pensumCode, Integer currentLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Timetable(String period, List<ExampleSection> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExampleSection(String sectionCode, String sinuCode, String courseName, int level, int credits,
                          int totalHours, String group, String subgroup, String professor, String sede,
                          List<ExampleMeeting> meetings) {

        SinuSection toSection(long shift) {
            return new SinuSection(sectionCode, sinuCode, courseName, level, credits, totalHours, group,
                    subgroup, professor, sede, meetings.stream().map(m -> m.toMeeting(shift)).toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExampleMeeting(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, List<ExampleRange> periods) {

        Meeting toMeeting(long shift) {
            return new Meeting(dayOfWeek, startTime, endTime, periods.stream()
                    .map(p -> new MeetingPeriod(p.from().plusDays(shift), p.to().plusDays(shift), p.room()))
                    .toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExampleRange(LocalDate from, LocalDate to, String room) {
    }
}
