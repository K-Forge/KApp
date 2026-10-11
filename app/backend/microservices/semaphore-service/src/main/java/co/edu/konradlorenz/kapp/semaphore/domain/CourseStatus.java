package co.edu.konradlorenz.kapp.semaphore.domain;

/**
 * The status of one item on a student's semáforo, normalised from SINU's.
 *
 * <p>There are six colours in the grid but five values here. <em>Blocked</em> is not one: SINU
 * has no such status. It is derived as a {@link #PENDING} item whose prerequisites are not all
 * {@link #PASSED}, and it is exactly the complement of {@code GET /api/semaphore/me/eligible}
 * within the pending set.
 *
 * <p>{@link #POSTPONED} is SINU's <em>aplazada</em>. How SINU uses it is still to be confirmed with
 * the university; until then it counts like a course not passed.
 */
public enum CourseStatus {
    PASSED,
    IN_PROGRESS,
    PENDING,
    FAILED,
    POSTPONED
}
