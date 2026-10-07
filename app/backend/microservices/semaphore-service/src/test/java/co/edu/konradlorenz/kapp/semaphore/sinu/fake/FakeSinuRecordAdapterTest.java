package co.edu.konradlorenz.kapp.semaphore.sinu.fake;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.semaphore.domain.Source;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The test SINU without a Spring context: the example's record, moved onto today's calendar. */
class FakeSinuRecordAdapterTest {

    private static final SinuPerson ANYONE = new SinuPerson("u", "u@konradlorenz.edu.co");

    private static FakeSinuRecordAdapter on(String instant) {
        return new FakeSinuRecordAdapter(Clock.fixed(Instant.parse(instant), ZoneId.of("America/Bogota")),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("the service's copy of the example is docs/api/sinu/example.json, byte for byte")
    void copyMatchesTheDocs() throws Exception {
        byte[] copy = new ClassPathResource(FakeSinuRecordAdapter.EXAMPLE).getContentAsByteArray();
        byte[] docs = Files.readAllBytes(Path.of("../../../../docs/api/sinu/example.json"));
        assertThat(copy).as("copy docs/api/sinu/example.json to src/main/resources/sinu/").isEqualTo(docs);
    }

    @Test
    @DisplayName("every student is the invented one: program 506, pensum 1015, level 5, and it says it is test data")
    void student() {
        FakeSinuRecordAdapter sinu = on("2026-10-05T15:00:00Z");
        assertThat(sinu.source()).isEqualTo(Source.TEST);
        assertThat(sinu.student(ANYONE)).hasValueSatisfying(s -> {
            assertThat(s.programCode()).isEqualTo("506");
            assertThat(s.pensumCode()).isEqualTo("1015");
            assertThat(s.currentLevel()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("the level in progress is always the current period, whenever today is")
    void recordFollowsTheCalendar() {
        for (String[] now : new String[][]{{"2026-10-05T15:00:00Z", "20262"}, {"2027-03-01T15:00:00Z", "20271"},
                {"2028-08-15T15:00:00Z", "20282"}}) {
            List<SinuRecord> records = on(now[0]).records(ANYONE);
            assertThat(records).filteredOn(r -> r.status().equals("EN CURSO"))
                    .isNotEmpty().allSatisfy(r -> assertThat(r.period()).isEqualTo(now[1]));
        }
    }

    @Test
    @DisplayName("periods move whole semesters, across years")
    void semesters() {
        assertThat(FakeSinuRecordAdapter.semesters(AcademicPeriod.parse("20271"), AcademicPeriod.parse("20262")))
                .isEqualTo(-1);
        assertThat(FakeSinuRecordAdapter.moved("20251", -1)).isEqualTo("20242");
        assertThat(FakeSinuRecordAdapter.moved("20242", 3)).isEqualTo("20261");
        assertThat(FakeSinuRecordAdapter.moved(null, 2)).isNull();
    }

    @Test
    @DisplayName("the elective bank is the current period's, for pensum 1015 only")
    void electiveBank() {
        FakeSinuRecordAdapter sinu = on("2026-10-05T15:00:00Z");
        assertThat(sinu.electiveBank("1015", AcademicPeriod.parse("20262"))).hasSize(3);
        assertThat(sinu.electiveBank("1015", AcademicPeriod.parse("20271"))).isEmpty();
        assertThat(sinu.electiveBank("1017", AcademicPeriod.parse("20262"))).isEmpty();
    }
}
