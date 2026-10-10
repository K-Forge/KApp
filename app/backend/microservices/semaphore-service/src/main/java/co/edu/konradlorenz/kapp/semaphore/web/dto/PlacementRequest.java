package co.edu.konradlorenz.kapp.semaphore.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Where to pin an item of a plan, and for an elective slot, the course from the elective bank the
 * student means to take in it.
 *
 * <p>The level is planning only: it never changes what the prerequisite rules allow.
 *
 * @param electiveSinuCode only for an elective slot; omitted or null, the slot keeps no chosen course
 */
public record PlacementRequest(
        @NotNull @Min(1) @Max(12) Integer plannedLevel,
        @Size(max = 20) String electiveSinuCode
) {
}
