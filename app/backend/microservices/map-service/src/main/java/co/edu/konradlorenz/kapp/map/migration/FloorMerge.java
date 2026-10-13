package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.Shape;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import co.edu.konradlorenz.kapp.map.web.dto.DoorDto;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.PointDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Carries what somebody did to a floor in the portal onto the snapshot's new drawing of it.
 *
 * <p>Three versions of the floor meet: the one the last load wrote (the base), the one in the
 * database - somebody's work on the base - and the snapshot's new one. Whatever somebody changed
 * since the base goes onto the new drawing, and everything else is the new drawing:
 * <ul>
 *   <li>A box given to an inventoried space. The floor editor moves the box's outline, unchanged,
 *       onto the space and drops the box; the space now takes the box's new outline.</li>
 *   <li>What was typed into a space - its name, type, door number, wing, other names, access, note,
 *       capacity - stays, field by field.</li>
 *   <li>A space somebody redrew keeps their outline; one they took off the plan stays off it; one
 *       they added stays; one they removed stays removed.</li>
 *   <li>The floor's own status, accessibility, note and direction, its corridors and its outline
 *       stay where somebody changed them.</li>
 * </ul>
 * When the result would not be a floor the editor could save - two rooms sharing floor, a room
 * outside the drawing - there is no merge: the floor is left as it is, as a load always did.
 */
final class FloorMerge {

    /**
     * @param floor   the merged floor, a version past the stored one: it is still somebody's work
     * @param spaces  every space on it
     * @param carried what was carried over, for the log
     */
    record Result(Floor floor, List<LayoutSpaceDto> spaces, List<String> carried) {
    }

    private FloorMerge() {
    }

    /** @return the merged floor, or null when the new drawing and somebody's work do not fit together */
    static Result merge(SurveySnapshot.SnapshotFloor base, Floor stored, List<SpaceDocument> storedSpaces,
                        SurveySnapshot.SnapshotFloor next) {
        Map<String, LayoutSpaceDto> was = byCode(base.spaces());
        Map<String, LayoutSpaceDto> mine = byCode(storedSpaces.stream().map(FloorMerge::layout).toList());
        Map<String, LayoutSpaceDto> theirs = byCode(next.spaces());
        Map<String, LayoutSpaceDto> merged = new LinkedHashMap<>(theirs);
        List<String> carried = new ArrayList<>();

        // Boxes given to inventoried spaces: the space holds the box's outline exactly.
        Map<String, String> took = new LinkedHashMap<>();
        for (LayoutSpaceDto space : mine.values()) {
            if (space.shape() == null) {
                continue;
            }
            LayoutSpaceDto before = was.get(space.code());
            if (before != null && space.shape().equals(before.shape())) {
                continue;
            }
            was.values().stream()
                    .filter(box -> box.shape() != null && !box.code().equals(space.code())
                            && !mine.containsKey(box.code()) && box.shape().equals(space.shape()))
                    .findFirst()
                    .ifPresent(box -> took.put(space.code(), box.code()));
        }
        for (Map.Entry<String, String> given : took.entrySet()) {
            LayoutSpaceDto box = theirs.get(given.getValue());
            LayoutSpaceDto space = merged.getOrDefault(given.getKey(), mine.get(given.getKey()));
            merged.remove(given.getValue());
            if (box != null && box.shape() != null) {
                merged.put(given.getKey(), withGeometry(space, box.shape(), box.doorsOrEmpty()));
                carried.add(given.getKey() + " takes " + given.getValue() + "'s new outline");
            } else {
                merged.put(given.getKey(), withGeometry(space, null, List.of()));
                carried.add(given.getKey() + " was " + given.getValue() + ", no longer drawn: left to place");
            }
        }

        for (LayoutSpaceDto space : mine.values()) {
            LayoutSpaceDto before = was.get(space.code());
            if (before == null) {
                if (!took.containsKey(space.code())) {
                    merged.put(space.code(), space);
                    carried.add(space.code() + " added in the portal");
                }
                continue;
            }
            LayoutSpaceDto into = merged.get(space.code());
            if (into == null) {
                // The new drawing dropped it; what somebody gave it is kept, off the plan.
                if (!sameDescription(before, space)) {
                    merged.put(space.code(), withGeometry(carry(space, before, space), null, List.of()));
                    carried.add(space.code() + " kept off the plan: the new drawing has no place for it");
                }
                continue;
            }
            LayoutSpaceDto result = carry(into, before, space);
            if (!took.containsKey(space.code()) && !Objects.equals(before.shape(), space.shape())) {
                result = withGeometry(result, space.shape(), space.doorsOrEmpty());
                carried.add(space.code() + " keeps the outline drawn in the portal");
            }
            if (!sameDescription(before, space)) {
                carried.add(space.code() + " keeps what was typed into it");
            }
            merged.put(space.code(), result);
        }

        for (LayoutSpaceDto before : was.values()) {
            if (!mine.containsKey(before.code()) && !took.containsValue(before.code())
                    && merged.remove(before.code()) != null) {
                carried.add(before.code() + " stays removed");
            }
        }

        Floor from = base.toFloor();
        Floor to = next.toFloor();
        List<LayoutSpaceDto> spaces = List.copyOf(merged.values());
        int width = Math.max(to.width(), spaces.stream().filter(s -> s.shape() != null)
                .flatMap(s -> s.shape().stream()).mapToInt(PointDto::x).max().orElse(0));
        int height = Math.max(to.height(), spaces.stream().filter(s -> s.shape() != null)
                .flatMap(s -> s.shape().stream()).mapToInt(PointDto::y).max().orElse(0));
        Floor floor = new Floor(to.code(), to.level(), pick(to.name(), from.name(), stored.name()),
                pick(to.status(), from.status(), stored.status()),
                pick(to.accessibility(), from.accessibility(), stored.accessibility()),
                pick(to.note(), from.note(), stored.note()), width, height,
                pick(to.top(), from.top(), stored.top()),
                pick(to.outline(), from.outline(), stored.outline()),
                pick(to.corridors(), from.corridors(), stored.corridors()),
                stored.version() + 1);
        return fits(floor, spaces) ? new Result(floor, spaces, carried) : null;
    }

    /** The space as a layout save would send it. */
    static LayoutSpaceDto layout(SpaceDocument space) {
        return new LayoutSpaceDto(space.code(), space.doorCode(), space.wing(), space.name(), space.typeCode(),
                space.aliases(), MapMapper.toPointDtos(space.shape()), MapMapper.toDoorDtos(space.doors()),
                space.accessVia(), space.accessibility(), space.note(), space.capacity());
    }

    /** Each field somebody changed from the base, on top of `into`. */
    private static LayoutSpaceDto carry(LayoutSpaceDto into, LayoutSpaceDto base, LayoutSpaceDto mine) {
        return new LayoutSpaceDto(into.code(),
                pick(into.doorCodeOrNull(), base.doorCodeOrNull(), mine.doorCodeOrNull()),
                pick(into.wingOrNull(), base.wingOrNull(), mine.wingOrNull()),
                pick(into.name(), base.name(), mine.name()),
                pick(into.typeCode(), base.typeCode(), mine.typeCode()),
                pick(into.aliasesOrEmpty(), base.aliasesOrEmpty(), mine.aliasesOrEmpty()),
                into.shape(), into.doorsOrEmpty(),
                pick(into.accessViaOrNull(), base.accessViaOrNull(), mine.accessViaOrNull()),
                pick(into.accessibility(), base.accessibility(), mine.accessibility()),
                pick(blankToNull(into.note()), blankToNull(base.note()), blankToNull(mine.note())),
                pick(into.capacity(), base.capacity(), mine.capacity()));
    }

    private static boolean sameDescription(LayoutSpaceDto a, LayoutSpaceDto b) {
        return Objects.equals(a.doorCodeOrNull(), b.doorCodeOrNull()) && Objects.equals(a.wingOrNull(), b.wingOrNull())
                && Objects.equals(a.name(), b.name()) && Objects.equals(a.typeCode(), b.typeCode())
                && Objects.equals(a.aliasesOrEmpty(), b.aliasesOrEmpty())
                && Objects.equals(a.accessViaOrNull(), b.accessViaOrNull())
                && Objects.equals(a.accessibility(), b.accessibility())
                && Objects.equals(blankToNull(a.note()), blankToNull(b.note()))
                && Objects.equals(a.capacity(), b.capacity());
    }

    /** Somebody's value where they changed it from the base; the new drawing's otherwise. */
    private static <T> T pick(T theirs, T base, T mine) {
        return Objects.equals(base, mine) ? theirs : mine;
    }

    private static LayoutSpaceDto withGeometry(LayoutSpaceDto space, List<PointDto> shape, List<DoorDto> doors) {
        return new LayoutSpaceDto(space.code(), space.doorCode(), space.wing(), space.name(), space.typeCode(),
                space.aliases(), shape, shape == null ? List.of() : doors, space.accessVia(), space.accessibility(),
                space.note(), space.capacity());
    }

    /** Every placed space inside the drawing, none folded over itself, and no two sharing floor. */
    private static boolean fits(Floor floor, List<LayoutSpaceDto> spaces) {
        List<List<Point>> shapes = spaces.stream().filter(s -> s.shape() != null)
                .map(s -> MapMapper.toPoints(s.shape())).toList();
        for (int i = 0; i < shapes.size(); i++) {
            List<Point> a = shapes.get(i);
            if (a.size() < 3 || !Shape.within(a, floor.width(), floor.height()) || !Shape.isSimple(a)) {
                return false;
            }
            for (int j = i + 1; j < shapes.size(); j++) {
                if (Shape.overlap(a, shapes.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, LayoutSpaceDto> byCode(List<LayoutSpaceDto> spaces) {
        Map<String, LayoutSpaceDto> map = new LinkedHashMap<>();
        spaces.forEach(s -> map.put(s.code(), s));
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
