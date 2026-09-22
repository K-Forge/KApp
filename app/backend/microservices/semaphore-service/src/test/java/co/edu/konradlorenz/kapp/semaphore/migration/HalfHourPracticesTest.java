package co.edu.konradlorenz.kapp.semaphore.migration;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumArea;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule {@link V007_KeepTheHalfHourPractices} moves the four rounded practices by, on
 * documents shaped like the three it touches. No database: what is worth checking here is which
 * figures move with an item and which do not, and that is arithmetic on a document.
 */
class HalfHourPracticesTest {

    private static final Map<String, Double> PSYCHOLOGY = Map.of("P5805", 4.5, "P5905", 4.5);

    /**
     * Psicología as the seed left it: two practices in the same area, an area whose hours are
     * the sum of its items, and a total of 174 the document itself prints - which its items,
     * rounded, add up past. The rest of the plan is one item; only the arithmetic matters here.
     */
    private static Pensum psychology(double first, double second) {
        return new Pensum("PSI-2020", "PSICOLOGIA", "Psicología", "Facultad de Psicología",
                "Plan de estudios 2020", PensumStatus.ACTIVE, 151, 174, 9,
                List.of(new PensumArea("PROFESIONAL", "Profesional", "#3E823E", 50, first + second + 40),
                        new PensumArea("BD", "Básica Disciplinar", "#539392", 37, 125)),
                List.of(course("P5805", 8, first, "PROFESIONAL"),
                        course("P5905", 9, second, "PROFESIONAL"),
                        course("P5000", 7, 40, "PROFESIONAL"),
                        course("P0000", 1, 125, "BD")));
    }

    private static PensumCourse course(String code, int level, double weeklyHours, String area) {
        return new PensumCourse(code, code, "Práctica " + code, level, 9, weeklyHours, area,
                false, List.of(), code);
    }

    @Test
    @DisplayName("the practice takes the printed half, and the area total follows it")
    void printedHoursAreRestored() {
        Pensum rounded = psychology(5, 5);

        Pensum restored = V007_KeepTheHalfHourPractices.withHours(rounded, PSYCHOLOGY, true);

        assertThat(restored.byPensumItemCode().get("P5805").weeklyHours()).isEqualTo(4.5);
        assertThat(restored.byPensumItemCode().get("P5905").weeklyHours()).isEqualTo(4.5);
        // Both practices sit in the same area, so it loses a whole hour, not a half.
        assertThat(restored.areas().getFirst().hours()).isEqualTo(49);
        assertThat(restored.areas().getLast().hours()).as("an untouched area").isEqualTo(125);
    }

    @Test
    @DisplayName("a total the document declares stays; the items are what move to meet it")
    void declaredTotalIsNotRecomputed() {
        // 174 is what the plan prints. Its items added up to 175 only because of the rounding.
        Pensum restored = V007_KeepTheHalfHourPractices.withHours(psychology(5, 5),
                PSYCHOLOGY, true);

        assertThat(restored.totalHours()).isEqualTo(174);
        assertThat(restored.courses().stream().mapToDouble(PensumCourse::weeklyHours).sum())
                .isEqualTo(174);
    }

    @Test
    @DisplayName("a total that was derived from the items moves with them")
    void derivedTotalFollowsTheItems() {
        // Marketing prints no total, so the seed added one up: it is not a declared figure and
        // has to stay equal to its items.
        Pensum marketing = new Pensum("MKT-2026", "MARKETING", "Marketing", "Escuela de Negocios",
                "Folleto publicado 2026", PensumStatus.DRAFT, 144, 166, 9,
                List.of(new PensumArea("PLAN", "Plan de estudios", "#539392", 144, 166)),
                List.of(course("MKT-902", 9, 2, "PLAN"), course("MKT-101", 1, 164, "PLAN")));

        Pensum restored = V007_KeepTheHalfHourPractices.withHours(marketing,
                Map.of("MKT-902", 1.5), true);

        assertThat(restored.totalHours()).isEqualTo(165.5);
        assertThat(restored.areas().getFirst().hours()).isEqualTo(165.5);
    }

    @Test
    @DisplayName("an item somebody has already corrected is left alone")
    void aCorrectedItemIsNotTouched() {
        Pensum corrected = psychology(4.5, 4.5);

        assertThat(V007_KeepTheHalfHourPractices.withHours(corrected, PSYCHOLOGY, true))
                .isSameAs(corrected);
    }

    @Test
    @DisplayName("an item holding neither figure is left alone, and its plan with it")
    void anUnrelatedValueIsNotTouched() {
        Pensum edited = psychology(6, 6);

        assertThat(V007_KeepTheHalfHourPractices.withHours(edited, PSYCHOLOGY, true))
                .isSameAs(edited);
    }

    @Test
    @DisplayName("running it twice changes nothing the second time")
    void isIdempotent() {
        Pensum once = V007_KeepTheHalfHourPractices.withHours(psychology(5, 5),
                PSYCHOLOGY, true);

        assertThat(V007_KeepTheHalfHourPractices.withHours(once, PSYCHOLOGY, true)).isSameAs(once);
    }

    @Test
    @DisplayName("the rollback rounds them back up and leaves the printed total where it was")
    void rollbackRoundsBackUp() {
        Pensum restored = V007_KeepTheHalfHourPractices.withHours(psychology(5, 5),
                PSYCHOLOGY, true);

        Pensum rounded = V007_KeepTheHalfHourPractices.withHours(restored, PSYCHOLOGY, false);

        assertThat(rounded.byPensumItemCode().get("P5805").weeklyHours()).isEqualTo(5);
        assertThat(rounded.areas().getFirst().hours()).isEqualTo(50);
        // Back to exactly what a database that never ran this held, 174 against 175 included.
        assertThat(rounded.totalHours()).isEqualTo(174);
    }
}
