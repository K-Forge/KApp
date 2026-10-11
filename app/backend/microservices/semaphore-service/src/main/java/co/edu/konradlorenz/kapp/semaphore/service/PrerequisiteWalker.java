package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Whether every prerequisite of a pensum item has been passed.
 *
 * <p>A prerequisite names another item of the same pensum by its {@code pensumItemCode}. One that is
 * missing from the semáforo - a pensum edited after a prerequisite was written - counts as not
 * passed: a course is never unlocked by a reference nobody can check.
 */
@Component
public class PrerequisiteWalker {

    /**
     * @param item             the item whose prerequisites to check
     * @param byPensumItemCode the student's semáforo, keyed by pensumItemCode
     */
    public boolean allPrerequisitesPassed(PensumCourse item, Map<String, SemaphoreEntry> byPensumItemCode) {
        return item.prerequisites().stream().allMatch(code -> {
            SemaphoreEntry entry = byPensumItemCode.get(code);
            return entry != null && entry.status() == CourseStatus.PASSED;
        });
    }
}
