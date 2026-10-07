package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ConflictException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.semaphore.domain.AcademicPlan;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.repository.AcademicPlanRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuElectiveOffering;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanRequest;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AcademicPlanUpdate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The student's own arrangements of their pensum.
 *
 * <p>Deliberately separate from {@link StudentProgressService}. Progress records what has been
 * passed; a plan records what the student intends to take and when. Keeping them apart is what
 * guarantees the rule that matters most here: <strong>moving a course never changes
 * eligibility</strong>. Nothing in this class touches a progress document, and
 * {@code getEligible} never reads a plan, so a student cannot unlock a course by dragging it
 * into an earlier semester.
 *
 * <p>Every method takes {@code userId} from the validated token. A plan belonging to somebody
 * else is reported as {@code 404}, not {@code 403}: a 403 would confirm that the identifier
 * exists, which is more than a caller who does not own it should learn.
 */
@Service
public class AcademicPlanService {

    /**
     * Enough for "what if I take Cálculo in the summer" several times over, and short of an
     * unbounded collection accumulating under one account.
     */
    static final int MAX_PLANS_PER_PENSUM = 10;

    private final AcademicPlanRepository plans;
    private final PensumRepository pensums;
    private final ElectiveBank electives;

    public AcademicPlanService(AcademicPlanRepository plans, PensumRepository pensums, ElectiveBank electives) {
        this.plans = plans;
        this.pensums = pensums;
        this.electives = electives;
    }

    /**
     * The plan as the API returns it: each elective chosen for a slot says whether this period's bank
     * offers it for that slot. Checked when the plan is read, not when it was saved, because the bank
     * changes every semester.
     */
    public AcademicPlanDto view(AcademicPlan plan) {
        boolean anyElective = plan.placements().stream().anyMatch(p -> p.electiveSinuCode() != null);
        List<SinuElectiveOffering> bank = anyElective ? electives.offerings(plan.pensumCode(), null) : List.of();
        return AcademicPlanDto.from(plan, placement -> placement.electiveSinuCode() == null ? null
                : bank.stream().anyMatch(o -> o.sinuCode().equals(placement.electiveSinuCode())
                        && o.fills(placement.pensumItemCode())));
    }

    public List<AcademicPlan> list(String userId) {
        return plans.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId);
    }

    public AcademicPlan get(String userId, String planId) {
        return requireOwn(userId, planId);
    }

    /**
     * @throws ResourceNotFoundException (404) if the pensum does not exist. A plan over a
     *                                    pensum nobody published cannot be drawn
     * @throws ConflictException         (409) at {@link #MAX_PLANS_PER_PENSUM}
     */
    public AcademicPlan create(String userId, AcademicPlanRequest request) {
        Pensum pensum = pensums.findById(request.pensumCode())
                .orElseThrow(() -> new ResourceNotFoundException("Pensum", request.pensumCode()));

        long existing = plans.countByUserIdAndPensumCode(userId, pensum.pensumCode());
        if (existing >= MAX_PLANS_PER_PENSUM) {
            throw new ConflictException(
                    "You already have %d plans for pensum %s, which is the maximum"
                            .formatted(MAX_PLANS_PER_PENSUM, pensum.pensumCode()),
                    List.of(new ApiError.FieldIssue("pensumCode", pensum.pensumCode())));
        }

        // The first plan for a pensum is the primary one. Anything else would leave a student
        // who created exactly one plan with no plan for the app to open on.
        boolean primary = existing == 0;
        return plans.save(AcademicPlan.create(userId, request.name(), pensum.pensumCode(), primary));
    }

    /**
     * @throws BusinessRuleException (400) if the body carries nothing to change, or asks to
     *                               demote a plan without promoting another
     */
    public AcademicPlan update(String userId, String planId, AcademicPlanUpdate update) {
        if (update.isEmpty()) {
            throw new BusinessRuleException("Nothing to update",
                    List.of(new ApiError.FieldIssue("name", "supply a name, primary, or both")));
        }
        if (Boolean.FALSE.equals(update.primary())) {
            throw new BusinessRuleException(
                    "A plan cannot be demoted directly; promote another one instead",
                    List.of(new ApiError.FieldIssue("primary",
                            "only true is accepted - promoting a plan demotes the current primary")));
        }

        AcademicPlan plan = requireOwn(userId, planId);
        if (update.name() != null) {
            plan = plan.renamedTo(update.name());
        }
        if (Boolean.TRUE.equals(update.primary()) && !plan.primary()) {
            demoteCurrentPrimary(userId, plan.pensumCode(), planId);
            plan = plan.withPrimary(true);
        }
        return plans.save(plan);
    }

    /**
     * Deleting the primary promotes the next plan by creation date, so a student who deletes
     * the one the app opens on is not left with a set of plans and no primary among them.
     */
    public void delete(String userId, String planId) {
        AcademicPlan plan = requireOwn(userId, planId);
        plans.deleteById(planId);

        if (plan.primary()) {
            plans.findByUserIdAndPensumCodeOrderByCreatedAtAsc(userId, plan.pensumCode()).stream()
                    .findFirst()
                    .ifPresent(next -> plans.save(next.withPrimary(true)));
        }
    }

    /**
     * Pins an item to a level, and an elective slot to the course chosen for it. Idempotent.
     *
     * @param electiveSinuCode null, or blank, for no chosen course
     * @throws BusinessRuleException (400) if the item is not in the plan's pensum - a plan carrying a
     *                               placement the grid has no square for would be dropped silently
     *                               by the client while the student believed the move was saved -
     *                               or if a course is chosen for an item that is not an elective slot
     */
    public AcademicPlan place(String userId, String planId, String pensumItemCode, int plannedLevel,
                              String electiveSinuCode) {
        AcademicPlan plan = requireOwn(userId, planId);
        Pensum pensum = pensums.findById(plan.pensumCode())
                .orElseThrow(() -> new ResourceNotFoundException("Pensum", plan.pensumCode()));

        PensumCourse item = pensum.findByPensumItemCode(pensumItemCode)
                .orElseThrow(() -> new BusinessRuleException(
                        "%s is not an item of pensum %s".formatted(pensumItemCode, plan.pensumCode()),
                        List.of(new ApiError.FieldIssue("pensumItemCode", "unknown in this pensum"))));
        String elective = electiveSinuCode == null || electiveSinuCode.isBlank() ? null : electiveSinuCode.trim();
        if (elective != null && !item.electiveSlot()) {
            throw new BusinessRuleException(
                    "%s is a fixed course: only an elective slot takes a course from the bank".formatted(pensumItemCode),
                    List.of(new ApiError.FieldIssue("electiveSinuCode", "only for an elective slot")));
        }
        return plans.save(plan.withPlacement(pensumItemCode, plannedLevel, elective));
    }

    /**
     * @throws ResourceNotFoundException (404) if the plan has no placement for that course. It
     *                                    was never moved, so there is nothing to undo, and
     *                                    answering 204 would tell a client it had reset
     *                                    something it had not
     */
    public AcademicPlan reset(String userId, String planId, String pensumItemCode) {
        AcademicPlan plan = requireOwn(userId, planId);
        if (plan.placementFor(pensumItemCode).isEmpty()) {
            throw new ResourceNotFoundException("Placement", pensumItemCode);
        }
        return plans.save(plan.withoutPlacement(pensumItemCode));
    }

    private AcademicPlan requireOwn(String userId, String planId) {
        return plans.findById(planId)
                .filter(p -> p.userId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Academic plan", planId));
    }

    /**
     * Demotes the current primary before the new one is written.
     *
     * <p>The order matters: the partial unique index forbids two primaries for one student and
     * pensum, so promoting first would collide with the plan being replaced. Demoting first
     * leaves a moment with no primary, which is the safe direction - a read landing in that
     * window sees the plans unordered, not an error.
     */
    private void demoteCurrentPrimary(String userId, String pensumCode, String exceptPlanId) {
        plans.findByUserIdAndPensumCodeAndPrimaryIsTrue(userId, pensumCode)
                .filter(current -> !current.id().equals(exceptPlanId))
                .ifPresent(current -> plans.save(current.withPrimary(false)));
    }
}
