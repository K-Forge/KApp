package co.edu.konradlorenz.kapp.semaphore.sinu.fake;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.semaphore.domain.Source;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuElectiveOffering;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecord;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuStudent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The test SINU: serves the invented student of {@code docs/api/sinu/example.json} while the
 * university has not opened the real one, and says so ({@link Source#TEST}).
 *
 * <p>Every student is served that student's record: program 506, pensum 1015, level 5, levels 1 to
 * 4 taken - one course lost and one postponed among them - and level 5 in progress. The example is
 * written for 2027-1; its periods are moved, whole semesters at a time, so that the level in
 * progress is always the current period. The elective bank is the current period's, for pensum 1015.
 */
@Component
@ConditionalOnProperty(name = "kapp.sinu.adapter", havingValue = "fake", matchIfMissing = true)
public class FakeSinuRecordAdapter implements SinuRecordPort {

    /** The example's own copy, which a test keeps identical to docs/api/sinu/example.json. */
    static final String EXAMPLE = "sinu/example.json";

    private final Clock clock;
    private final Example example;

    public FakeSinuRecordAdapter(Clock clock, ObjectMapper json) {
        this.clock = clock;
        try (InputStream in = new ClassPathResource(EXAMPLE).getInputStream()) {
            this.example = json.readValue(in, Example.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the test SINU's record " + EXAMPLE, e);
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
    public Optional<SinuStudent> student(SinuPerson person) {
        Person p = example.person();
        return Optional.of(new SinuStudent(p.programCode(), p.pensumCode(), p.currentLevel()));
    }

    @Override
    public List<SinuRecord> records(SinuPerson person) {
        int shift = semesters(examplePeriod(), current());
        return example.records().stream()
                .map(r -> new SinuRecord(r.sinuCode(), r.name(), r.status(), moved(r.period(), shift), r.grade()))
                .toList();
    }

    @Override
    public List<SinuElectiveOffering> electiveBank(String pensumCode, AcademicPeriod period) {
        Bank bank = example.electiveBank();
        if (!bank.pensumCode().equals(pensumCode) || !period.equals(current())) {
            return List.of();
        }
        return bank.offerings().stream()
                .map(o -> new SinuElectiveOffering(o.sinuCode(), o.name(), o.credits(), o.weeklyHours(), o.slots()))
                .toList();
    }

    private AcademicPeriod current() {
        return periodOn(LocalDate.now(clock));
    }

    /** The period the example's own timetable is written for, which is its student's current one. */
    private AcademicPeriod examplePeriod() {
        return AcademicPeriod.parse(example.timetable().period());
    }

    /** How many semesters from one period to the other: 20262 to 20271 is one. */
    static int semesters(AcademicPeriod from, AcademicPeriod to) {
        return (to.year() * 2 + to.semester()) - (from.year() * 2 + from.semester());
    }

    static String moved(String period, int semesters) {
        if (period == null) {
            return null;
        }
        AcademicPeriod p = AcademicPeriod.parse(period);
        int index = p.year() * 2 + (p.semester() - 1) + semesters;
        return new AcademicPeriod(index / 2, index % 2 + 1).toString();
    }

    // ---------------------------------------------------- the shape of docs/api/sinu/example.json

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Example(Person person, Timetable timetable, List<Record> records, Bank electiveBank) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(String programCode, String pensumCode, int currentLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Timetable(String period) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Record(String sinuCode, String name, String status, String period, Integer grade) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Bank(String period, String pensumCode, List<Offering> offerings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Offering(String sinuCode, String name, int credits, double weeklyHours, List<String> slots) {
    }
}
