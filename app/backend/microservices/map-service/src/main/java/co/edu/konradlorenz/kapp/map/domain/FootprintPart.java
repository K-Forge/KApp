package co.edu.konradlorenz.kapp.map.domain;

import java.util.List;

/**
 * One part of a building as the city's cadastre records it from above: its outline on the ground
 * and how many floors it rises.
 *
 * <p>A building is seldom one block. The Edificio Central is a strip of five floors along the
 * Calle 63, a core of eight and a south wing of six and four over its auditorium, and the
 * cadastre keeps each as a part of its own - which is what lets a map draw the building with its
 * true shape and tell its wings apart.
 *
 * @param lot       the cadastral lot the part stands on, as the cadastre codes it; null for a part
 *                  the cadastre does not record, found on site - a roofed stretch it missed
 * @param floors    how many floors it rises above the street: the cadastre's count, unless
 *                  checked on site
 * @param lowestFloor the lowest floor above the street it takes in, when it is not the first:
 *                  2 for what the upper floors carry out over a portico or a sidewalk, which the
 *                  cadastre, seeing from above, draws as if it reached the ground. Null for a part
 *                  that stands on the street, as almost every part does
 * @param basements how many it goes below
 * @param wing      the code of the building's wing it belongs to, when somebody has said; null
 *                  otherwise
 * @param ring      its outline, as GeoJSON writes it: {@code [lon, lat]} points, the first
 *                  repeated at the end
 */
public record FootprintPart(String lot, int floors, Integer lowestFloor, int basements, String wing,
        List<List<Double>> ring) {

    public FootprintPart {
        // The first floor is the default, and written as nothing: every part stored before the field existed.
        lowestFloor = lowestFloor == null || lowestFloor <= 1 ? null : lowestFloor;
        ring = ring == null ? List.of() : ring.stream().map(List::copyOf).toList();
    }
}
