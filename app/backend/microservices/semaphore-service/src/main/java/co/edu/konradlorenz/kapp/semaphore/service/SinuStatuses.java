package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * SINU's statuses, as the semáforo's own five.
 *
 * <p>The spellings are a guess until the university sends SINU's list (docs/api/sinu/README.md,
 * question 1): the ones a real report and the test SINU use, in either gender, read without
 * accents or case. A status nobody has seen yet is shown as {@link CourseStatus#PENDING} - not
 * passed, not lost - and the entry keeps SINU's word in {@code sinuStatus}, so a client shows what
 * SINU said rather than a guess.
 */
public final class SinuStatuses {

    private static final Map<String, CourseStatus> KNOWN = Map.ofEntries(
            Map.entry("APROBADA", CourseStatus.PASSED),
            Map.entry("APROBADO", CourseStatus.PASSED),
            Map.entry("EN CURSO", CourseStatus.IN_PROGRESS),
            Map.entry("CURSANDO", CourseStatus.IN_PROGRESS),
            Map.entry("PERDIDA", CourseStatus.FAILED),
            Map.entry("PERDIDO", CourseStatus.FAILED),
            Map.entry("REPROBADA", CourseStatus.FAILED),
            Map.entry("REPROBADO", CourseStatus.FAILED),
            Map.entry("APLAZADA", CourseStatus.POSTPONED),
            Map.entry("APLAZADO", CourseStatus.POSTPONED));

    private SinuStatuses() {
    }

    public static CourseStatus of(String sinuStatus) {
        return sinuStatus == null ? CourseStatus.PENDING : KNOWN.getOrDefault(fold(sinuStatus), CourseStatus.PENDING);
    }

    /** @return whether the status is one of SINU's this class knows */
    public static boolean isKnown(String sinuStatus) {
        return sinuStatus != null && KNOWN.containsKey(fold(sinuStatus));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
