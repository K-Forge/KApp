package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A locatable space: a classroom, a lab, an office, a bathroom, a lift - anything somebody
 * may need to find.
 *
 * <p>Spaces are a flat collection rather than an array nested inside the building. The
 * primary access pattern is "find a room by name anywhere on campus", and a nested model
 * would hand the client whole building documents to filter for itself. Flat also means the
 * one text index this collection is allowed can cover every searchable field at once.
 *
 * <p>{@code buildingCode}, {@code campus} and {@code floorLevel} are denormalised copies of
 * the building's own fields, so a search result renders without a second call.
 * {@code BuildingService} propagates them whenever the originals change.
 *
 * <h2>Two codes, because many spaces have no number on the door</h2>
 * The survey settled it: the JAAB, Bienestar, the administrative building and Medio
 * Universitario name dependencies - "Dirección de Revistas Científicas", "Gimnasio" - not
 * numbered rooms. Every space still needs an identifier, so {@code code} is always present and
 * unique within the building; {@code doorCode} is what is actually printed on the door, and is
 * null when nothing is. <strong>Only {@code doorCode} is ever shown.</strong> A generated code
 * on screen looks exactly like a real one, and a student would repeat it at a counter where
 * nobody has heard of it - the same reason invented course codes are never displayed.
 *
 * <h2>Placed, or only inventoried</h2>
 * A floor's information plaque says what is on it long before anybody draws where. Those spaces
 * are stored with no {@code shape} and are placed when someone matches them with a room on the
 * floor. The search finds them either way.
 *
 * @param code          identifier within the building. Equal to {@code doorCode} for a numbered
 *                      room, generated for everything else. This is also the key
 *                      {@code schedule-service} stores against a class
 * @param doorCode      exactly as printed on the door - {@code 503-S} - or null
 * @param baseCode      {@code doorCode} without its wing's suffix: {@code 503} for {@code 503-S},
 *                      so someone who was told "el 503" still finds all the 503s. Null without a
 *                      door code
 * @param wing          code of one of the building's {@link Wing}s, or null
 * @param typeCode      a {@link SpaceTypeDocument} code
 * @param floorCode     the {@link Floor} it is on
 * @param floorLevel    that floor's level, copied for ordering
 * @param accessVia     code of the lift, staircase or entrance that serves this space: "piso 4,
 *                      sube por el ascensor central". Explicit rather than derived from
 *                      proximity: whoever walks the floor knows which lift people actually use
 * @param accessibility whether this space is reachable without stairs, or null to take the
 *                      floor's. Set it where the space differs: EC's north terrace sits on a
 *                      floor whose central wing has lifts, and is only reachable by a staircase
 * @param note          how to get there, when {@code accessVia} is not enough - "solo por la
 *                      escalera norte, desde el P5"
 * @param shape         the room's outline on its floor, corners in order, or null while it is only
 *                      inventoried; see {@link Shape}
 * @param doors         its ways in, each on its outline; empty until somebody marks them
 */
@Document(collection = "spaces")
public record SpaceDocument(
        @Id String id,
        String code,
        String doorCode,
        String baseCode,
        String wing,
        String name,
        String typeCode,
        String buildingId,
        String buildingCode,
        String campus,
        String floorCode,
        double floorLevel,
        List<String> aliases,
        List<Point> shape,
        List<Door> doors,
        String accessVia,
        Accessibility accessibility,
        String note,
        Integer capacity,
        boolean placeholder,
        Instant createdAt,
        Instant updatedAt
) {

    public SpaceDocument {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        shape = shape == null || shape.isEmpty() ? null : List.copyOf(shape);
        doors = doors == null ? List.of() : List.copyOf(doors);
    }

    /**
     * The door code with its wing's suffix removed: {@code 503} for {@code 503-S}.
     *
     * <p>Only a suffix one of the building's wings declares is stripped. Splitting on the last
     * dash looked simpler and was wrong for most codes on campus: {@code S-01} in a basement would
     * have lost everything after the {@code S}, and two unrelated staircases would have collapsed
     * into one base code.
     */
    public static String baseCodeOf(String doorCode, List<Wing> wings) {
        if (doorCode == null) {
            return null;
        }
        for (Wing wing : wings) {
            String suffix = wing.doorSuffix();
            if (suffix != null && !suffix.isEmpty()
                    && doorCode.length() > suffix.length()
                    && doorCode.toUpperCase().endsWith(suffix.toUpperCase())) {
                return doorCode.substring(0, doorCode.length() - suffix.length());
            }
        }
        return doorCode;
    }

    /** @return true when the space has a shape on the floor, false while it is only inventoried */
    public boolean isPlaced() {
        return shape != null;
    }

    /** Two spaces overlap only when both are placed and they share some floor, not just a wall. */
    public boolean overlaps(SpaceDocument other) {
        return isPlaced() && other.isPlaced() && Shape.overlap(shape, other.shape);
    }

    /** @return this space's own accessibility, or the floor's when it does not state one */
    public Accessibility effectiveAccessibility(Floor floor) {
        return accessibility != null ? accessibility : floor.accessibility();
    }
}
