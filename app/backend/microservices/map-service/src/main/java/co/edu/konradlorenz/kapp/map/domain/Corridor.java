package co.edu.konradlorenz.kapp.map.domain;

import java.util.List;

/**
 * A walkable route across a floor, drawn as a coloured line.
 *
 * <p>Corridors are what turn a set of rooms into a floor somebody can read. The mockup
 * draws them as coloured segments, and the colour is stored rather than derived so the
 * person capturing a floor can match what is painted on the actual walls - several
 * buildings colour-code their wings, and guessing a palette would throw that away.
 *
 * <p>Embedded in {@link Floor}, like the floor itself is embedded in its building: nothing
 * ever asks for a corridor without already knowing which floor it is on.
 *
 * @param path the points the corridor runs through, in walking order. A polyline, not a
 *             rectangle: real corridors bend
 */
public record Corridor(
        String code,
        String name,
        String color,
        List<Point> path
) {

    public Corridor {
        path = path == null ? List.of() : List.copyOf(path);
    }
}
