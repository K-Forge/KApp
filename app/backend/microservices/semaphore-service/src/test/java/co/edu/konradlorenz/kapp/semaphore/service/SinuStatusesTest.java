package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SinuStatusesTest {

    @Test
    @DisplayName("SINU's four known statuses, in either gender")
    void known() {
        assertThat(SinuStatuses.of("APROBADA")).isEqualTo(CourseStatus.PASSED);
        assertThat(SinuStatuses.of("APROBADO")).isEqualTo(CourseStatus.PASSED);
        assertThat(SinuStatuses.of("EN CURSO")).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(SinuStatuses.of("PERDIDA")).isEqualTo(CourseStatus.FAILED);
        assertThat(SinuStatuses.of("APLAZADA")).isEqualTo(CourseStatus.POSTPONED);
        assertThat(SinuStatuses.of("APLAZADO")).isEqualTo(CourseStatus.POSTPONED);
    }

    @Test
    @DisplayName("read without case, accents or extra spaces")
    void folded() {
        assertThat(SinuStatuses.of(" aprobada ")).isEqualTo(CourseStatus.PASSED);
        assertThat(SinuStatuses.of("En  curso")).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(SinuStatuses.of("Aplazáda")).isEqualTo(CourseStatus.POSTPONED);
    }

    @Test
    @DisplayName("an unknown status, or none, is PENDING")
    void unknown() {
        assertThat(SinuStatuses.of("HOMOLOGADA")).isEqualTo(CourseStatus.PENDING);
        assertThat(SinuStatuses.isKnown("HOMOLOGADA")).isFalse();
        assertThat(SinuStatuses.of(null)).isEqualTo(CourseStatus.PENDING);
    }
}
