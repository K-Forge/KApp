package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.Semaphore;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Where a student stands in their pensum, read from their semáforo, for the rules a plan follows.
 *
 * <ul>
 *   <li><strong>The current level</strong> is the highest level with a course {@code IN_PROGRESS}.
 *       With none in progress, between two periods, it is the level after the highest one with a
 *       course {@code PASSED}; level 1 when nothing is passed yet.</li>
 *   <li><strong>The next period's level</strong> is the one after the current level while a course
 *       is in progress, and the current level itself when none is: that is the level the student
 *       takes next.</li>
 * </ul>
 *
 * The levels are the pensum's, not SINU's {@code currentLevel}: what a student has passed and is
 * taking is what decides where a course can still go.
 *
 * @param current the level the student is in
 * @param next    the level of the next period, where a failed course has to be taken again
 */
public record PlanLevels(int current, int next) {

    public static PlanLevels of(Pensum pensum, Semaphore semaphore) {
        Map<String, Integer> levelOf = pensum.courses().stream()
                .collect(Collectors.toMap(PensumCourse::pensumItemCode, PensumCourse::level, (a, b) -> a));
        Function<CourseStatus, OptionalInt> highest = status -> semaphore.courses().stream()
                .filter(entry -> entry.status() == status && levelOf.containsKey(entry.pensumItemCode()))
                .mapToInt(entry -> levelOf.get(entry.pensumItemCode()))
                .max();

        OptionalInt inProgress = highest.apply(CourseStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            return new PlanLevels(inProgress.getAsInt(), inProgress.getAsInt() + 1);
        }
        int afterPassed = highest.apply(CourseStatus.PASSED).orElse(0) + 1;
        return new PlanLevels(afterPassed, afterPassed);
    }
}
