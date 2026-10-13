package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.Placement;

/**
 * Wire shape of {@code Placement}.
 *
 * @param electiveOffered whether this period's elective bank offers {@code electiveSinuCode} for
 *                        that slot; null when no course is chosen. Written as null rather than left
 *                        out: the contract makes it required
 */
public record PlacementDto(String pensumItemCode, int plannedLevel, String electiveSinuCode, Boolean electiveOffered) {

    public static PlacementDto from(Placement placement, Boolean electiveOffered) {
        return new PlacementDto(placement.pensumItemCode(), placement.plannedLevel(), placement.electiveSinuCode(),
                electiveOffered);
    }
}
