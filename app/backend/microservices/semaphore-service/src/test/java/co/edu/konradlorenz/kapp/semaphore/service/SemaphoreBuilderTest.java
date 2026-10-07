package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumArea;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A SINU record laid over a pensum, without Spring: which record each item takes, which attempt
 * counts, and how elective slots are filled.
 */
class SemaphoreBuilderTest {

    /** Two fixed courses, a course with no SINU code, and three elective slots on three levels. */
    private static final Pensum PENSUM = new Pensum("T-1", "T", "Test", "Facultad", "Reforma", PensumStatus.ACTIVE,
            18, 18, 3, List.of(new PensumArea("A", "Área", "#539392", 18, 18)), List.of(
            course("11015", "11015", "Precálculo", 1, false),
            course("12015", "12015", "Cálculo I", 2, false),
            course("T-103", null, "Seminario", 1, false),
            course("59075", "59075", "Electiva I", 1, true),
            course("59078", "59078", "Electiva II", 2, true),
            course("59098", "59098", "Electiva VI", 3, true)));

    private static PensumCourse course(String item, String sinu, String name, int level, boolean slot) {
        return new PensumCourse(item, name, level, 3, 3, "A", slot, List.of(), sinu);
    }

    private static Map<String, SemaphoreEntry> build(SinuRecord... records) {
        return SemaphoreBuilder.entries(PENSUM, List.of(records)).stream()
                .collect(Collectors.toMap(SemaphoreEntry::pensumItemCode, Function.identity()));
    }

    @Test
    @DisplayName("one entry per item, in the pensum's order, and an item with no record is PENDING")
    void mirrorsThePensum() {
        List<SemaphoreEntry> entries = SemaphoreBuilder.entries(PENSUM, List.of());
        assertThat(entries).extracting(SemaphoreEntry::pensumItemCode)
                .containsExactly("59075", "11015", "T-103", "12015", "59078", "59098");
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.status()).isEqualTo(CourseStatus.PENDING);
            assertThat(e.sinuStatus()).isNull();
        });
    }

    @Test
    @DisplayName("a fixed course takes the record with its SINU code, with SINU's own word kept")
    void fixedCourse() {
        SemaphoreEntry entry = build(new SinuRecord("11015", "PRECÁLCULO", "APROBADA", "20251", 42)).get("11015");
        assertThat(entry.status()).isEqualTo(CourseStatus.PASSED);
        assertThat(entry.sinuStatus()).isEqualTo("APROBADA");
        assertThat(entry.period()).isEqualTo("20251");
        assertThat(entry.grade()).isEqualTo(42);
        assertThat(entry.resolvedSinuCode()).isNull();
    }

    @Test
    @DisplayName("a course taken twice shows the latest attempt")
    void latestAttempt() {
        SemaphoreEntry entry = build(
                new SinuRecord("12015", "CÁLCULO I", "PERDIDA", "20252", 21),
                new SinuRecord("12015", "CÁLCULO I", "APROBADA", "20261", 35)).get("12015");
        assertThat(entry.status()).isEqualTo(CourseStatus.PASSED);
        assertThat(entry.period()).isEqualTo("20261");
    }

    @Test
    @DisplayName("within one period, the attempt furthest along wins")
    void furthestAlongInOnePeriod() {
        SemaphoreEntry entry = build(
                new SinuRecord("12015", "CÁLCULO I", "EN CURSO", "20262", null),
                new SinuRecord("12015", "CÁLCULO I", "APLAZADA", "20262", null)).get("12015");
        assertThat(entry.status()).isEqualTo(CourseStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("an item whose SINU code is not known stays PENDING whatever SINU has")
    void noSinuCode() {
        assertThat(build(new SinuRecord("T-103", "SEMINARIO", "APROBADA", "20251", 40)).get("T-103").status())
                .isEqualTo(CourseStatus.PENDING);
    }

    @Test
    @DisplayName("a slot listed under its own code is filled by that record, as a real report lists it")
    void slotByItsOwnCode() {
        SemaphoreEntry slot = build(new SinuRecord("59098", "ELECTIVA VI", "EN CURSO", "20262", null)).get("59098");
        assertThat(slot.status()).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(slot.resolvedSinuCode()).isEqualTo("59098");
        assertThat(slot.resolvedName()).isEqualTo("ELECTIVA VI");
    }

    @Test
    @DisplayName("electives taken under their own code fill the empty slots by level, the oldest first")
    void slotsByTheCourseItself() {
        Map<String, SemaphoreEntry> entries = build(
                new SinuRecord("59214", "INTELIGENCIA ARTIFICIAL APLICADA", "APROBADA", "20261", 45),
                new SinuRecord("59211", "COMPUTACIÓN EN LA NUBE", "APROBADA", "20252", 41));
        assertThat(entries.get("59075").resolvedSinuCode()).isEqualTo("59211");
        assertThat(entries.get("59075").resolvedName()).isEqualTo("COMPUTACIÓN EN LA NUBE");
        assertThat(entries.get("59075").status()).isEqualTo(CourseStatus.PASSED);
        assertThat(entries.get("59078").resolvedSinuCode()).isEqualTo("59214");
        assertThat(entries.get("59098").status()).isEqualTo(CourseStatus.PENDING);
    }

    @Test
    @DisplayName("a slot filled under its own code is not filled twice, and a record with no slot left is not shown")
    void noDoubleFilling() {
        Map<String, SemaphoreEntry> entries = build(
                new SinuRecord("59075", "ELECTIVA I", "APROBADA", "20251", 40),
                new SinuRecord("59078", "ELECTIVA II", "APROBADA", "20252", 40),
                new SinuRecord("59098", "ELECTIVA VI", "APROBADA", "20261", 40),
                new SinuRecord("59211", "COMPUTACIÓN EN LA NUBE", "APROBADA", "20262", 41));
        assertThat(entries.values()).extracting(SemaphoreEntry::resolvedSinuCode)
                .doesNotContain("59211");
        assertThat(entries).hasSize(6);
    }

    @Test
    @DisplayName("a status nobody has seen yet is PENDING, and SINU's word is kept for the client to show")
    void unknownStatus() {
        SemaphoreEntry entry = build(new SinuRecord("11015", "PRECÁLCULO", "HOMOLOGADA", "20251", null)).get("11015");
        assertThat(entry.status()).isEqualTo(CourseStatus.PENDING);
        assertThat(entry.sinuStatus()).isEqualTo("HOMOLOGADA");
    }
}
