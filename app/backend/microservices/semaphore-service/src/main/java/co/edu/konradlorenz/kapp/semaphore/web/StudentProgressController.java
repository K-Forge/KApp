package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.common.security.CurrentUser;
import co.edu.konradlorenz.kapp.semaphore.security.StudentOnly;
import co.edu.konradlorenz.kapp.semaphore.service.SemaphoreService;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ProgressSummaryDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.StudentSemaphoreDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's semáforo, read from SINU: the grid, its summary, and what the prerequisites allow
 * next. Read-only - statuses, grades and electives are SINU's - and always the caller's own, from the
 * validated token: no route reads another student's, administrators included.
 */
@RestController
@RequestMapping("/api/semaphore/me")
@Tag(name = "Student Progress")
public class StudentProgressController {

    private final SemaphoreService semaphores;

    public StudentProgressController(SemaphoreService semaphores) {
        this.semaphores = semaphores;
    }

    @GetMapping
    @StudentOnly
    @Operation(summary = "Get the caller's semáforo",
            description = "Read from SINU: one entry per item of the student's pensum. 404 when SINU has "
                    + "no program for the caller.")
    public StudentSemaphoreDto getMine() {
        return StudentSemaphoreDto.from(semaphores.semaphore(caller()));
    }

    @GetMapping("/summary")
    @StudentOnly
    @Operation(summary = "Get the caller's progress summary",
            description = "Credit totals derived from the semáforo, recomputed on every call.")
    public ProgressSummaryDto getSummary() {
        return semaphores.summary(caller());
    }

    @GetMapping("/eligible")
    @StudentOnly
    @Operation(summary = "List the courses the caller can take next",
            description = "PENDING items whose prerequisites are all PASSED. Taking a course is still "
                    + "done in SINU.")
    public List<PensumCourseDto> getEligible() {
        return semaphores.eligible(caller());
    }

    private static SinuPerson caller() {
        return new SinuPerson(CurrentUser.id(), CurrentUser.email());
    }
}
