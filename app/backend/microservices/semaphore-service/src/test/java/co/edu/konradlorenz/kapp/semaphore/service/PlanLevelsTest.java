package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumArea;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Semaphore;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import co.edu.konradlorenz.kapp.semaphore.domain.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Where a student stands, from the levels of the courses they passed and are taking. */
class PlanLevelsTest {

    /** Two courses on each of the levels 1 to 3. */
    private static final Pensum PENSUM = new Pensum("T-1", "T", "Test", "Facultad", "Reforma", PensumStatus.ACTIVE,
            18, 18, 3, List.of(new PensumArea("A", "Área", "#539392", 18, 18)), List.of(
            course("A1", 1), course("B1", 1), course("A2", 2), course("B2", 2), course("A3", 3), course("B3", 3)));

    private static PensumCourse course(String item, int level) {
        return new PensumCourse(item, item, level, 3, 3, "A", false, List.of(), item);
    }

    private static Semaphore semaphore(CourseStatus... statuses) {
        List<String> items = List.of("A1", "B1", "A2", "B2", "A3", "B3");
        List<SemaphoreEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CourseStatus status = i < statuses.length ? statuses[i] : CourseStatus.PENDING;
            entries.add(new SemaphoreEntry(items.get(i), status, null, null, null, null, null));
        }
        return new Semaphore("u-1", "T", "T-1", 9, Source.TEST, Instant.EPOCH, entries);
    }

    @Test
    @DisplayName("with a course in progress, the student is at its level and the next period is the one after")
    void inProgress() {
        PlanLevels levels = PlanLevels.of(PENSUM, semaphore(CourseStatus.PASSED, CourseStatus.FAILED,
                CourseStatus.IN_PROGRESS, CourseStatus.PASSED));

        assertThat(levels).isEqualTo(new PlanLevels(2, 3));
    }

    @Test
    @DisplayName("between periods, the student is at the level after the last one with a course passed, and takes it next")
    void betweenPeriods() {
        PlanLevels levels = PlanLevels.of(PENSUM, semaphore(CourseStatus.PASSED, CourseStatus.PASSED,
                CourseStatus.PASSED, CourseStatus.FAILED));

        assertThat(levels).isEqualTo(new PlanLevels(3, 3));
    }

    @Test
    @DisplayName("with nothing passed or in progress, the student is at level 1")
    void nothingYet() {
        assertThat(PlanLevels.of(PENSUM, semaphore())).isEqualTo(new PlanLevels(1, 1));
    }

    @Test
    @DisplayName("the levels are the pensum's, not the level SINU writes on the record")
    void notSinusLevel() {
        assertThat(PlanLevels.of(PENSUM, semaphore(CourseStatus.IN_PROGRESS)).current()).isEqualTo(1);
    }
}
