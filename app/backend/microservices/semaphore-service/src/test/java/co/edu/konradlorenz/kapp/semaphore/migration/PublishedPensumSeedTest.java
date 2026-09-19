package co.edu.konradlorenz.kapp.semaphore.migration;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.ProgramRepository;
import co.edu.konradlorenz.kapp.semaphore.service.PensumValidator;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * What {@code V006} leaves in a fresh database, checked against what the printed plans say.
 *
 * <p>The seed is generated from {@code docs/pensums/}, where every figure below is traced to
 * the PDF it came from. A regeneration that shifts a course between semesters, loses an item
 * or breaks a total fails here with the old number, instead of in front of a student.
 *
 * <h2>Declared totals are what the document prints</h2>
 * {@link Pensum#totalCredits()} and {@link Pensum#totalHours()} are never recomputed. Where a
 * plan prints a total its courses do not reach, the difference stays visible and is pinned in
 * {@link #KNOWN_GAPS}, each one explained in {@code docs/pensums/README.md}.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class PublishedPensumSeedTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    /** pensumCode -> {declared credits, computed credits, declared hours, computed hours}. */
    private static final Map<String, int[]> KNOWN_GAPS = Map.of(
            "1017", new int[]{143, 143, 195, 197},
            "PSI-2020", new int[]{151, 151, 174, 175},
            "ENC-2026", new int[]{33, 0, 0, 0},
            "MPC-2026", new int[]{52, 0, 0, 0},
            "MIAC-2026", new int[]{34, 0, 0, 0},
            "MPCL-2026", new int[]{59, 0, 0, 0});

    @Autowired
    private PensumRepository pensums;
    @Autowired
    private ProgramRepository programs;
    @Autowired
    private PensumValidator validator;
    @Autowired
    private Validator beanValidator;

    private Pensum pensum(String code) {
        return pensums.findById(code).orElseThrow();
    }

    @Test
    @DisplayName("all 23 published pensums are loaded, each under a program that exists")
    void everyPublishedPensumIsLoaded() {
        List<Pensum> published = new V006_SeedThePublishedPensums().readPensums();

        assertThat(published).hasSize(23);
        for (Pensum seeded : published) {
            Pensum loaded = pensum(seeded.pensumCode());
            assertThat(loaded.courses()).as(seeded.pensumCode()).hasSameSizeAs(seeded.courses());
            assertThat(programs.existsById(loaded.programCode())).as(loaded.programCode()).isTrue();
        }
    }

    @Test
    @DisplayName("every seeded pensum passes the same validation the write endpoints apply")
    void everySeededPensumIsValid() {
        for (Pensum seeded : new V006_SeedThePublishedPensums().readPensums()) {
            PensumDto dto = PensumDto.from(seeded);
            assertThat(beanValidator.validate(dto)).as(seeded.pensumCode()).isEmpty();
            assertThatCode(() -> validator.validate(dto)).as(seeded.pensumCode()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("declared totals match the courses, except the gaps the documents themselves carry")
    void declaredTotalsMatchOrAreKnownGaps() {
        for (Pensum seeded : new V006_SeedThePublishedPensums().readPensums()) {
            int credits = seeded.courses().stream().mapToInt(PensumCourse::credits).sum();
            int hours = seeded.courses().stream().mapToInt(PensumCourse::weeklyHours).sum();
            int[] expected = KNOWN_GAPS.getOrDefault(seeded.pensumCode(),
                    new int[]{credits, credits, hours, hours});
            assertThat(new int[]{seeded.totalCredits(), credits, seeded.totalHours(), hours})
                    .as(seeded.pensumCode()).containsExactly(expected);
        }
    }

    @Test
    @DisplayName("only the four plans printed with course codes are ACTIVE, one per program")
    void onlyCodedPlansAreActive() {
        Map<String, List<String>> activeByProgram = pensums.findAll().stream()
                .filter(p -> p.status() == PensumStatus.ACTIVE)
                .collect(Collectors.groupingBy(Pensum::programCode,
                        Collectors.mapping(Pensum::pensumCode, Collectors.toList())));

        assertThat(activeByProgram).containsOnly(
                Map.entry("506", List.of("1015")),
                Map.entry("MATEMATICAS", List.of("1017")),
                Map.entry("ING-INDUSTRIAL", List.of("1020")),
                Map.entry("PSICOLOGIA", List.of("PSI-2020")));
    }

    @Test
    @DisplayName("placeholder course codes carry no sinuCode; printed ones carry their own")
    void sinuCodeMarksInstitutionalCodes() {
        assertThat(pensum("MKT-2026").courses()).allSatisfy(c -> assertThat(c.sinuCode()).isNull());
        assertThat(pensum("1015").courses())
                .allSatisfy(c -> assertThat(c.sinuCode()).isEqualTo(c.pensumItemCode()));
    }

    // ── Ingeniería de Sistemas 1015: the reconstruction V002 seeded is gone ──────────────

    @Test
    @DisplayName("1015 is the printed grid: 51 items, none of the reconstructed IS-* codes")
    void reconstructionIsReplaced() {
        Pensum pensum = pensum("1015");

        assertThat(pensum.courses()).hasSize(51);
        assertThat(V006_SeedThePublishedPensums.isReconstruction(pensum)).isFalse();
        assertThat(pensum.totalCredits()).isEqualTo(143);
        assertThat(pensum.totalHours()).isEqualTo(194);
        assertThat(pensum.levels()).isEqualTo(9);
        assertThat(pensum.areaCodes()).containsExactly("CB", "BIS", "ISA", "SI");
    }

    @Test
    @DisplayName("1015 per level: credits and weekly contact hours as the grid prints them")
    void sistemasLevelTotals() {
        int[][] expected = {{16, 21}, {16, 21}, {16, 22}, {16, 23}, {16, 21}, {16, 18}, {16, 19}, {17, 20}, {14, 29}};
        Pensum pensum = pensum("1015");
        for (int level = 1; level <= 9; level++) {
            int l = level;
            var items = pensum.courses().stream().filter(c -> c.level() == l).toList();
            assertThat(items.stream().mapToInt(PensumCourse::credits).sum()).as("level %d credits", l)
                    .isEqualTo(expected[l - 1][0]);
            assertThat(items.stream().mapToInt(PensumCourse::weeklyHours).sum()).as("level %d hours", l)
                    .isEqualTo(expected[l - 1][1]);
        }
    }

    @Test
    @DisplayName("1015 keeps the drawn prerequisites and its six elective slots")
    void sistemasPrerequisitesAndElectives() {
        Pensum pensum = pensum("1015");

        assertThat(pensum.findByAddressableCode("12015").orElseThrow().prerequisites()).containsExactly("11015");
        assertThat(pensum.findByAddressableCode("46012").orElseThrow().prerequisites()).containsExactly("45012");
        assertThat(pensum.findByAddressableCode("39020").orElseThrow().prerequisites()).containsExactly("56201");
        assertThat(pensum.courses().stream().filter(PensumCourse::electiveSlot).map(PensumCourse::pensumItemCode))
                .containsExactlyInAnyOrder("59075", "59078", "59085", "59088", "59096", "59098");
    }
}
