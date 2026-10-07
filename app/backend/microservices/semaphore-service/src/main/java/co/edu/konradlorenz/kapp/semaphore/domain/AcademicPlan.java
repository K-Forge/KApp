package co.edu.konradlorenz.kapp.semaphore.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A student's own arrangement of a pensum - what the mockups call a saved semaforo, next to
 * the immutable <em>Semaforo Original</em>.
 *
 * <h2>Why only the deltas</h2>
 * {@code placements} holds nothing but the courses the student moved. A course that does not
 * appear is taken where the pensum puts it.
 *
 * <p>Copying the whole pensum into every plan would have three costs and no benefit. The
 * document would carry sixty entries to express one change; a correction to the published
 * pensum would reach no existing plan, so students would keep planning against a version the
 * university has withdrawn; and drawing the faint outline in the original column - which the
 * design calls for - would need the pensum fetched anyway to know where the course came
 * from. Storing the delta means the client already holds both numbers.
 *
 * <h2>What a plan is not</h2>
 * A plan never affects eligibility. {@code GET /api/semaphore/me/eligible} is computed from
 * prerequisites actually approved and does not read this collection at all. Moving a course is
 * planning, not passing it, and a semaforo that unlocked courses by dragging them would be
 * lying to the student it is meant to inform.
 *
 * @param primary the plan the app opens on. Exactly one per student and pensum, enforced by a
 *                partial unique index - see {@code V003_AcademicPlanIndexes}
 */
@Document(collection = "academicPlans")
public record AcademicPlan(
        @Id String id,
        String userId,
        String name,
        String pensumCode,
        boolean primary,
        List<Placement> placements,
        Instant createdAt,
        Instant updatedAt
) {

    public AcademicPlan {
        placements = placements == null ? List.of() : List.copyOf(placements);
    }

    public static AcademicPlan create(String userId, String name, String pensumCode, boolean primary) {
        Instant now = Instant.now();
        return new AcademicPlan(null, userId, name, pensumCode, primary, List.of(), now, now);
    }

    public Optional<Placement> placementFor(String pensumItemCode) {
        return placements.stream().filter(p -> p.pensumItemCode().equals(pensumItemCode)).findFirst();
    }

    /**
     * @return a copy with the item pinned to {@code level}, and an elective slot to the course chosen
     *         for it, replacing any existing placement for the item. Idempotent, which is what lets
     *         the endpoint be a PUT.
     */
    public AcademicPlan withPlacement(String pensumItemCode, int level, String electiveSinuCode) {
        List<Placement> next = new ArrayList<>(placements.stream()
                .filter(p -> !p.pensumItemCode().equals(pensumItemCode))
                .toList());
        next.add(new Placement(pensumItemCode, level, electiveSinuCode));
        next.sort((a, b) -> a.pensumItemCode().compareTo(b.pensumItemCode()));
        return new AcademicPlan(id, userId, name, pensumCode, primary, next, createdAt, Instant.now());
    }

    /** @return a copy without a placement for the item; it returns to its pensum level */
    public AcademicPlan withoutPlacement(String pensumItemCode) {
        return new AcademicPlan(id, userId, name, pensumCode, primary,
                placements.stream().filter(p -> !p.pensumItemCode().equals(pensumItemCode)).toList(),
                createdAt, Instant.now());
    }

    public AcademicPlan renamedTo(String newName) {
        return new AcademicPlan(id, userId, newName, pensumCode, primary, placements,
                createdAt, Instant.now());
    }

    public AcademicPlan withPrimary(boolean isPrimary) {
        return new AcademicPlan(id, userId, name, pensumCode, isPrimary, placements,
                createdAt, Instant.now());
    }
}
