package co.edu.konradlorenz.kapp.schedule.sinu.fake;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Source;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuSection;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test SINU without a Spring context: which period a date is in, where a semester's classes
 * start, and the example timetable moved onto the current period without losing a weekday, a gap
 * or a room.
 */
class FakeSinuTimetableAdapterTest {

    private static final SinuPerson STUDENT = new SinuPerson("s", "s@konradlorenz.edu.co", SinuPerson.Role.STUDENT);
    private static final SinuPerson PROFESSOR = new SinuPerson("p", "p@konradlorenz.edu.co", SinuPerson.Role.PROFESSOR);

    private static FakeSinuTimetableAdapter on(String instant) {
        return new FakeSinuTimetableAdapter(Clock.fixed(Instant.parse(instant), ZoneId.of("America/Bogota")),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("the service's copy of the example is docs/api/sinu/example.json, byte for byte")
    void copyMatchesTheDocs() throws Exception {
        byte[] copy = new ClassPathResource(FakeSinuTimetableAdapter.EXAMPLE).getContentAsByteArray();
        byte[] docs = Files.readAllBytes(Path.of("../../../../docs/api/sinu/example.json"));
        assertThat(copy).as("copy docs/api/sinu/example.json to src/main/resources/sinu/").isEqualTo(docs);
    }

    @Test
    @DisplayName("a date's period is its year and its half of the year")
    void periodOn() {
        FakeSinuTimetableAdapter sinu = on("2026-10-05T15:00:00Z");
        assertThat(sinu.periodOn(LocalDate.parse("2026-01-15"))).isEqualTo(AcademicPeriod.parse("20261"));
        assertThat(sinu.periodOn(LocalDate.parse("2026-06-30"))).isEqualTo(AcademicPeriod.parse("20261"));
        assertThat(sinu.periodOn(LocalDate.parse("2026-07-01"))).isEqualTo(AcademicPeriod.parse("20262"));
        assertThat(sinu.periodOn(LocalDate.parse("2026-12-31"))).isEqualTo(AcademicPeriod.parse("20262"));
    }

    @Test
    @DisplayName("classes start on the first Monday from 1 February, or from 25 July")
    void firstMonday() {
        assertThat(FakeSinuTimetableAdapter.firstMonday(AcademicPeriod.parse("20262"))).isEqualTo("2026-07-27");
        assertThat(FakeSinuTimetableAdapter.firstMonday(AcademicPeriod.parse("20271"))).isEqualTo("2027-02-01");
        assertThat(FakeSinuTimetableAdapter.firstMonday(AcademicPeriod.parse("20281"))).isEqualTo("2028-02-07");
    }

    @Test
    @DisplayName("it says its timetables are invented, and has only the current period")
    void sourceAndPeriods() {
        FakeSinuTimetableAdapter sinu = on("2026-10-05T15:00:00Z");
        assertThat(sinu.source()).isEqualTo(Source.TEST);
        assertThat(sinu.periods(STUDENT)).containsExactly(AcademicPeriod.parse("20262"));
        assertThat(sinu.timetable(STUDENT, AcademicPeriod.parse("20261"))).isEmpty();
        assertThat(sinu.timetable(STUDENT, AcademicPeriod.parse("20271"))).isEmpty();
    }

    @Test
    @DisplayName("the example, written for 2027-1, keeps its own dates in 2027-1")
    void examplePeriodKeepsItsDates() {
        SinuTimetable timetable = on("2027-03-01T15:00:00Z").timetable(STUDENT, AcademicPeriod.parse("20271")).orElseThrow();
        MeetingPeriod first = timetable.sections().get(0).meetings().get(0).periods().get(0);
        assertThat(first.from()).isEqualTo("2027-02-01");
        assertThat(first.to()).isEqualTo("2027-03-15");
    }

    @Test
    @DisplayName("moved onto another period, every range still starts and ends on its meeting's weekday")
    void movedTimetableKeepsWeekdays() {
        for (String now : new String[]{"2026-10-05T15:00:00Z", "2027-09-01T15:00:00Z", "2028-04-10T15:00:00Z"}) {
            FakeSinuTimetableAdapter sinu = on(now);
            AcademicPeriod period = sinu.periods(STUDENT).get(0);
            SinuTimetable timetable = sinu.timetable(STUDENT, period).orElseThrow();
            for (SinuSection section : timetable.sections()) {
                for (Meeting meeting : section.meetings()) {
                    for (MeetingPeriod range : meeting.periods()) {
                        assertThat(range.from().getDayOfWeek()).as(now + " " + section.sectionCode()).isEqualTo(meeting.dayOfWeek());
                        assertThat(range.to().getDayOfWeek()).as(now + " " + section.sectionCode()).isEqualTo(meeting.dayOfWeek());
                    }
                }
            }
            assertThat(timetable.sections().get(0).meetings().get(0).periods().get(0).from())
                    .isEqualTo(FakeSinuTimetableAdapter.firstMonday(period));
        }
    }

    @Test
    @DisplayName("a student gets all six sections with the program; a professor the two they teach, without it")
    void studentAndProfessor() {
        FakeSinuTimetableAdapter sinu = on("2026-10-05T15:00:00Z");
        AcademicPeriod period = AcademicPeriod.parse("20262");

        SinuTimetable student = sinu.timetable(STUDENT, period).orElseThrow();
        assertThat(student.sections()).hasSize(6);
        assertThat(student.programCode()).isEqualTo("506");
        assertThat(student.pensumCode()).isEqualTo("1015");
        assertThat(student.level()).isEqualTo(5);

        SinuTimetable professor = sinu.timetable(PROFESSOR, period).orElseThrow();
        assertThat(professor.sections()).extracting(SinuSection::sectionCode).containsExactly("3101", "3105");
        assertThat(professor.sections()).allSatisfy(s -> assertThat(s.professor()).isEqualTo("DOCENTE EJEMPLO UNO"));
        assertThat(professor.programCode()).isNull();
        assertThat(professor.pensumCode()).isNull();
        assertThat(professor.level()).isNull();
    }
}
