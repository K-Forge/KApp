package co.edu.konradlorenz.kapp.semaphore.domain;

/**
 * One item of a student's semáforo: the status SINU gives a single pensum item, plus the course
 * that filled it when the item is an elective slot. Built on every read, never stored.
 *
 * @param sinuStatus       the status exactly as SINU writes it; null for an item SINU has no
 *                         record of
 * @param period           when the item was taken, {@code YYYYS}; null while it has not been
 * @param grade            on the university's {@code 0..50} scale, when SINU gives one; else null
 * @param resolvedSinuCode for an elective slot, the code of the course that filled it, as SINU
 *                         records it; null for a fixed course and for a slot SINU has not filled
 * @param resolvedName     the name of that course; null whenever resolvedSinuCode is
 */
public record SemaphoreEntry(
        String pensumItemCode,
        CourseStatus status,
        String sinuStatus,
        String period,
        Integer grade,
        String resolvedSinuCode,
        String resolvedName
) {

    /** An item SINU has no record of: not taken yet. */
    public static SemaphoreEntry pending(String pensumItemCode) {
        return new SemaphoreEntry(pensumItemCode, CourseStatus.PENDING, null, null, null, null, null);
    }
}
