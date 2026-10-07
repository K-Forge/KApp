package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the one rule the "semaforo" is named for: a pensum item is eligible
 * only when every prerequisite has actually settled as {@code PASSED}.
 */
class PrerequisiteWalkerTest {

    private final PrerequisiteWalker walker = new PrerequisiteWalker();

    @Test
    @DisplayName("an item with no prerequisites is always eligible")
    void noPrerequisitesIsEligible() {
        PensumCourse item = course("20015", List.of());

        assertThat(walker.allPrerequisitesPassed(item, Map.of())).isTrue();
    }

    @Test
    @DisplayName("a prerequisite recorded PASSED unlocks the item")
    void passedPrerequisiteUnlocks() {
        PensumCourse item = course("10024", List.of("10011"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "10011", entry("10011", CourseStatus.PASSED));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isTrue();
    }

    @Test
    @DisplayName("IN_PROGRESS does not unlock - a course being taken has not settled")
    void inProgressPrerequisiteDoesNotUnlock() {
        PensumCourse item = course("10024", List.of("10011"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "10011", entry("10011", CourseStatus.IN_PROGRESS));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isFalse();
    }

    @Test
    @DisplayName("a POSTPONED prerequisite does not unlock: aplazada is not passed")
    void postponedPrerequisiteDoesNotUnlock() {
        PensumCourse item = course("10024", List.of("10011"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "10011", entry("10011", CourseStatus.POSTPONED));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isFalse();
    }

    @Test
    @DisplayName("a FAILED prerequisite does not unlock")
    void failedPrerequisiteDoesNotUnlock() {
        PensumCourse item = course("10024", List.of("10011"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "10011", entry("10011", CourseStatus.FAILED));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isFalse();
    }

    @Test
    @DisplayName("a PENDING prerequisite does not unlock")
    void pendingPrerequisiteDoesNotUnlock() {
        PensumCourse item = course("10024", List.of("10011"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "10011", entry("10011", CourseStatus.PENDING));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isFalse();
    }

    @Test
    @DisplayName("a missing prerequisite entry does not unlock")
    void missingPrerequisiteEntryDoesNotUnlock() {
        PensumCourse item = course("10024", List.of("10011"));

        assertThat(walker.allPrerequisitesPassed(item, Map.of())).isFalse();
    }

    @Test
    @DisplayName("all of several prerequisites must be PASSED, not just some")
    void allOfSeveralPrerequisitesMustBePassed() {
        PensumCourse item = course("IS-ARQSOFT", List.of("46018", "20037"));
        Map<String, SemaphoreEntry> byCode = Map.of(
                "46018", entry("46018", CourseStatus.PASSED),
                "20037", entry("20037", CourseStatus.IN_PROGRESS));

        assertThat(walker.allPrerequisitesPassed(item, byCode)).isFalse();
    }

    private static PensumCourse course(String code, List<String> prerequisites) {
        return new PensumCourse(code, "Course " + code, 1, 3, 4, "BIS", false, prerequisites, null);
    }

    private static SemaphoreEntry entry(String code, CourseStatus status) {
        return new SemaphoreEntry(code, status, null, null, null, null, null);
    }
}
