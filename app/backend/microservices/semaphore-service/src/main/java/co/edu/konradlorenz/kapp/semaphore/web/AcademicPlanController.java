package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.common.security.CurrentUser;
import co.edu.konradlorenz.kapp.semaphore.security.StudentOnly;
import co.edu.konradlorenz.kapp.semaphore.service.AcademicPlanService;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanRequest;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanUpdate;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PlacementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The student's saved arrangements of their pensum: the one thing in this service a student writes.
 *
 * <p>{@code ROLE_STUDENT} only, and identity always comes from {@link CurrentUser#id()} - the
 * validated JWT subject - never from the path. A professor or staff member follows no program and
 * is refused here exactly as on the rest of {@code /api/semaphore/me/**}, and nobody reads another
 * student's plans, administrators included.
 */
@RestController
@RequestMapping("/api/semaphore/me/plans")
@Tag(name = "Academic Plans")
public class AcademicPlanController {

    private final AcademicPlanService planService;

    public AcademicPlanController(AcademicPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @StudentOnly
    @Operation(summary = "List the caller's academic plans")
    public List<AcademicPlanDto> listPlans() {
        return planService.list(CurrentUser.id()).stream().map(planService::view).toList();
    }

    @PostMapping
    @StudentOnly
    @Operation(summary = "Create an academic plan")
    public ResponseEntity<AcademicPlanDto> createPlan(@Valid @RequestBody AcademicPlanRequest body) {
        AcademicPlanDto created = planService.view(planService.create(CurrentUser.id(), body));
        return ResponseEntity.created(URI.create("/api/semaphore/me/plans/" + created.id()))
                .body(created);
    }

    @GetMapping("/{planId}")
    @StudentOnly
    @Operation(summary = "Get one academic plan")
    public AcademicPlanDto getPlan(@PathVariable String planId) {
        return planService.view(planService.get(CurrentUser.id(), planId));
    }

    @PatchMapping("/{planId}")
    @StudentOnly
    @Operation(summary = "Rename a plan or make it the primary one")
    public AcademicPlanDto updatePlan(@PathVariable String planId,
                                       @Valid @RequestBody AcademicPlanUpdate body) {
        return planService.view(planService.update(CurrentUser.id(), planId, body));
    }

    @DeleteMapping("/{planId}")
    @StudentOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an academic plan")
    public void deletePlan(@PathVariable String planId) {
        planService.delete(CurrentUser.id(), planId);
    }

    @PutMapping("/{planId}/placements/{pensumItemCode}")
    @StudentOnly
    @Operation(summary = "Move a course to a different level",
            description = "For an elective slot, electiveSinuCode names the course from the elective "
                    + "bank. Does NOT affect eligibility, which is computed from approved prerequisites.")
    public AcademicPlanDto placeCourse(@PathVariable String planId,
                                        @PathVariable String pensumItemCode,
                                        @Valid @RequestBody PlacementRequest body) {
        return planService.view(planService.place(CurrentUser.id(), planId, pensumItemCode,
                body.plannedLevel(), body.electiveSinuCode()));
    }

    @DeleteMapping("/{planId}/placements/{pensumItemCode}")
    @StudentOnly
    @Operation(summary = "Return a course to the level the pensum gives it")
    public AcademicPlanDto resetCourse(@PathVariable String planId, @PathVariable String pensumItemCode) {
        return planService.view(planService.reset(CurrentUser.id(), planId, pensumItemCode));
    }
}
