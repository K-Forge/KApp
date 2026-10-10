package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.Semaphore;

import java.time.Instant;
import java.util.List;

/** Wire shape of {@code StudentSemaphore}: a student's semáforo as SINU has it. */
public record StudentSemaphoreDto(
        String userId,
        String programCode,
        String pensumCode,
        int currentLevel,
        String source,
        Instant readAt,
        List<SemaphoreEntryDto> courses
) {

    public static StudentSemaphoreDto from(Semaphore semaphore) {
        return new StudentSemaphoreDto(semaphore.userId(), semaphore.programCode(), semaphore.pensumCode(),
                semaphore.currentLevel(), semaphore.source().name(), semaphore.readAt(),
                semaphore.courses().stream().map(SemaphoreEntryDto::from).toList());
    }
}
