package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;

/**
 * Wire shape of {@code StudentProgressCourse}: one item of a semáforo. Every nullable field is
 * written as null rather than left out, because the contract makes all of them required.
 */
public record SemaphoreEntryDto(
        String pensumItemCode,
        CourseStatus status,
        String sinuStatus,
        String period,
        Integer grade,
        String resolvedSinuCode,
        String resolvedName
) {

    public static SemaphoreEntryDto from(SemaphoreEntry entry) {
        return new SemaphoreEntryDto(entry.pensumItemCode(), entry.status(), entry.sinuStatus(), entry.period(),
                entry.grade(), entry.resolvedSinuCode(), entry.resolvedName());
    }
}
