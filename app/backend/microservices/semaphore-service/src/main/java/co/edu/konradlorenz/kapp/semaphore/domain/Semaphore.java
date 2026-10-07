package co.edu.konradlorenz.kapp.semaphore.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A student's semáforo as SINU has it: their program, pensum and level, and one entry per item of
 * that pensum, in the pensum's order. Built on every read from the student's SINU record and the
 * pensum, never stored.
 *
 * @param readAt when the record was read from SINU; it is kept a few minutes at most
 */
public record Semaphore(
        String userId,
        String programCode,
        String pensumCode,
        int currentLevel,
        Source source,
        Instant readAt,
        List<SemaphoreEntry> courses
) {

    public Semaphore {
        courses = courses == null ? List.of() : List.copyOf(courses);
    }

    public Map<String, SemaphoreEntry> byPensumItemCode() {
        Map<String, SemaphoreEntry> index = new LinkedHashMap<>();
        courses.forEach(entry -> index.put(entry.pensumItemCode(), entry));
        return index;
    }
}
