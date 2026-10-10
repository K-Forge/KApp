package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.AcademicPlan;
import co.edu.konradlorenz.kapp.semaphore.domain.Placement;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * A plan as the API returns it.
 *
 * <p>{@code userId} is absent: every one of these endpoints answers for the caller's own
 * plans and takes identity from the token, so echoing it back would only invite a client to
 * treat it as something it could send.
 */
public record AcademicPlanDto(
        String id,
        String name,
        String pensumCode,
        boolean primary,
        List<PlacementDto> placements,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param offered whether each placement's chosen elective is offered; see {@link PlacementDto} */
    public static AcademicPlanDto from(AcademicPlan plan, Function<Placement, Boolean> offered) {
        return new AcademicPlanDto(plan.id(), plan.name(), plan.pensumCode(), plan.primary(),
                plan.placements().stream().map(p -> PlacementDto.from(p, offered.apply(p))).toList(),
                plan.createdAt(), plan.updatedAt());
    }
}
