package co.edu.konradlorenz.kapp.map.domain;

import java.util.List;

/**
 * One floor of a building, embedded in its {@link BuildingDocument}.
 *
 * <p>Floors are embedded rather than kept in their own collection because they are never
 * queried on their own: every read that wants a floor already knows the building. Spaces
 * are the opposite case and live in a flat collection of their own.
 *
 * <h2>A code, not a number, is what identifies a floor</h2>
 * The floor used to be addressed by an integer level. The campus does not fit that: the JAAB has
 * a mezzanine between its first and second floors, Medio Universitario enters at {@code P0}, and
 * Bienestar's directory numbers its terrace 5 while everyone calls it the terrace. So a floor is
 * identified by {@code code} - {@code S1}, {@code P0}, {@code P1}, {@code MEZZ}, {@code T} - which
 * is also what the API addresses it by, and {@code level} only orders the floors: the mezzanine is
 * {@code 1.5}, between the two it sits between.
 *
 * <h2>A grid, not a photograph</h2>
 * The floor is described as data - rooms occupying cells of a grid, corridors tracing paths
 * through it - and the client draws it. A schematic floor is captured by walking it, which the
 * team can do itself without waiting for anybody's architectural plans.
 *
 * @param code          identifier within the building, and the path segment the API uses
 * @param level         vertical order only; decimal so a mezzanine can sit between two floors
 * @param status        how far the drawing is from verified; see {@link FloorStatus}
 * @param accessibility whether the floor is reachable without stairs; its spaces inherit it
 * @param note          how to get here when it is not obvious - "se sube por la escalera exterior"
 * @param version       bumped by every layout save, so two people editing the same floor cannot
 *                      silently overwrite each other; the second save is refused instead
 */
public record Floor(
        String code,
        double level,
        String name,
        FloorStatus status,
        Accessibility accessibility,
        String note,
        int gridRows,
        int gridColumns,
        List<Corridor> corridors,
        long version
) {

    public Floor {
        corridors = corridors == null ? List.of() : List.copyOf(corridors);
        status = status == null ? FloorStatus.UNMAPPED : status;
        accessibility = accessibility == null ? Accessibility.UNKNOWN : accessibility;
    }
}
