package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Door;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.Shape;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.web.dto.CorridorDto;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.PointDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The rules a floor's spaces have to satisfy together, checked the same way whether one space is
 * written or a whole floor at once.
 *
 * <p>Both doors check the same thing - the complete set of spaces the floor would hold
 * afterwards - which is what keeps them from disagreeing. The single-space endpoints pass the
 * floor's stored spaces with the new one in place; the layout save passes what the editor
 * sent. A rule written once cannot pass on one path and fail on the other.
 *
 * <p>Every rule is here because breaking it is invisible on screen rather than obviously wrong: a
 * room outside the floor simply does not render, two overlapping rooms draw one on top of the
 * other, and an {@code accessVia} that names nothing makes the app quietly say nothing about how
 * to get there.
 */
final class SpaceRules {

    /**
     * What a check found.
     *
     * @param invalid  mistakes in what was sent: a missing type, a room off the floor
     * @param overlaps two placed spaces covering the same area. Reported apart because on the
     *                 single-space endpoints it is a conflict with what is already stored (409),
     *                 while in a layout save it is a mistake in the drawing itself (400)
     */
    record Findings(List<ApiError.FieldIssue> invalid, List<ApiError.FieldIssue> overlaps) {

        boolean clean() {
            return invalid.isEmpty() && overlaps.isEmpty();
        }
    }

    private SpaceRules() {
    }

    /**
     * @param spaces     every space the floor would hold, placed or not
     * @param labels     how each one is named in a message: {@code spaces[3]}, or the empty
     *                   string on the single-space endpoints, where the field name alone is clearer
     * @param reportable the positions in {@code spaces} whose problems are the caller's business.
     *                   A layout save reports all of them; a single-space write only the one it
     *                   sent, so a pre-existing problem between two stored rooms cannot make an
     *                   unrelated room impossible to edit
     * @param elsewhere  the building's spaces on its other floors, which share its codes and can
     *                   be what an {@code accessVia} points at
     */
    static Findings check(BuildingDocument building, Floor floor, List<LayoutSpaceDto> spaces,
                          List<String> labels, java.util.Set<Integer> reportable,
                          List<SpaceDocument> elsewhere, Map<String, SpaceTypeDocument> types) {
        List<ApiError.FieldIssue> invalid = new ArrayList<>();
        List<ApiError.FieldIssue> overlaps = new ArrayList<>();

        Map<String, SpaceDocument> otherFloorsByCode = new HashMap<>();
        Map<String, SpaceDocument> otherFloorsByDoor = new HashMap<>();
        for (SpaceDocument other : elsewhere) {
            otherFloorsByCode.put(other.code(), other);
            if (other.doorCode() != null) {
                otherFloorsByDoor.put(other.doorCode(), other);
            }
        }

        // Counted up front so every copy of a duplicate is reported at its own position: the
        // one the caller sent might be the second copy or the first.
        Map<String, Integer> codeCounts = new HashMap<>();
        Map<String, Integer> doorCounts = new HashMap<>();
        Map<String, LayoutSpaceDto> byCode = new HashMap<>();
        for (LayoutSpaceDto space : spaces) {
            codeCounts.merge(space.code(), 1, Integer::sum);
            if (space.doorCodeOrNull() != null) {
                doorCounts.merge(space.doorCodeOrNull(), 1, Integer::sum);
            }
            byCode.putIfAbsent(space.code(), space);
        }

        for (int i = 0; i < spaces.size(); i++) {
            LayoutSpaceDto space = spaces.get(i);
            String at = labels.get(i);
            List<ApiError.FieldIssue> sink = reportable.contains(i) ? invalid : new ArrayList<>();

            if (codeCounts.get(space.code()) > 1) {
                sink.add(issue(at, "code", "'%s' is used twice on this floor".formatted(space.code())));
            }
            SpaceDocument clash = otherFloorsByCode.get(space.code());
            if (clash != null) {
                sink.add(issue(at, "code", "'%s' is already a space on floor %s of this building"
                        .formatted(space.code(), clash.floorCode())));
            }

            String door = space.doorCodeOrNull();
            if (door != null) {
                if (doorCounts.get(door) > 1) {
                    sink.add(issue(at, "doorCode", "door '%s' appears twice on this floor".formatted(door)));
                }
                SpaceDocument doorClash = otherFloorsByDoor.get(door);
                if (doorClash != null) {
                    sink.add(issue(at, "doorCode", "door '%s' is already on floor %s of this building"
                            .formatted(door, doorClash.floorCode())));
                }
            }

            if (!types.containsKey(space.typeCode())) {
                sink.add(issue(at, "typeCode",
                        "'%s' is not in the space type catalogue".formatted(space.typeCode())));
            }

            String wing = space.wingOrNull();
            if (wing != null && building.wing(wing).isEmpty()) {
                sink.add(issue(at, "wing", "'%s' is not one of the wings of building %s"
                        .formatted(wing, building.code())));
            }

            checkPlacement(floor, space, at, sink);
            checkAccessVia(building, space, at, byCode, otherFloorsByCode, types, sink);
        }

        for (int i = 0; i < spaces.size(); i++) {
            for (int j = i + 1; j < spaces.size(); j++) {
                if ((reportable.contains(i) || reportable.contains(j))
                        && overlap(spaces.get(i), spaces.get(j))) {
                    overlaps.add(issue(labels.get(reportable.contains(i) ? i : j), "shape",
                            "'%s' and '%s' would overlap"
                            .formatted(spaces.get(i).code(), spaces.get(j).code())));
                }
            }
        }

        return new Findings(invalid, overlaps);
    }

    /**
     * The floor's own drawing - its outline and its corridors - has to stay on the floor, or it is
     * drawn off the edge; an outline also has to close without crossing itself.
     */
    static List<ApiError.FieldIssue> checkFloorDrawing(int width, int height, List<PointDto> outline,
                                                        List<CorridorDto> corridors) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        if (!outline.isEmpty()) {
            List<Point> points = MapMapper.toPoints(outline);
            if (points.size() < 3 || !Shape.within(points, width, height) || !Shape.isSimple(points)
                    || Shape.area(points) == 0) {
                issues.add(new ApiError.FieldIssue("outline",
                        "the outline must be a closed shape of three or more corners, inside the %d x %d floor, "
                                .formatted(width, height) + "that does not cross itself"));
            }
        }
        for (int i = 0; i < corridors.size(); i++) {
            for (PointDto point : corridors.get(i).path()) {
                if (point.x() > width || point.y() > height) {
                    issues.add(new ApiError.FieldIssue("corridors[%d].path".formatted(i),
                            "(%d, %d) is outside the %d x %d floor".formatted(point.x(), point.y(), width, height)));
                    break;
                }
            }
        }
        return issues;
    }

    /**
     * A placed space's outline: three or more corners, all on the floor, enclosing some area and
     * never crossing itself. A shape that folds over itself has no inside a client could fill.
     */
    private static void checkPlacement(Floor floor, LayoutSpaceDto space, String at,
                                       List<ApiError.FieldIssue> invalid) {
        if (space.shape() == null) {
            if (!space.doorsOrEmpty().isEmpty()) {
                invalid.add(issue(at, "doors", "'%s' has doors but no shape to put them on"
                        .formatted(space.code())));
            }
            return;
        }
        List<Point> points = MapMapper.toPoints(space.shape());
        if (!Shape.within(points, floor.width(), floor.height())) {
            invalid.add(issue(at, "shape", "'%s' reaches outside the %d x %d floor"
                    .formatted(space.code(), floor.width(), floor.height())));
        } else if (Shape.area(points) == 0) {
            invalid.add(issue(at, "shape", "'%s' encloses no area".formatted(space.code())));
        } else if (!Shape.isSimple(points)) {
            invalid.add(issue(at, "shape", "'%s' crosses itself".formatted(space.code())));
        } else {
            List<Door> doors = MapMapper.toDoors(space.doorsOrEmpty());
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door.from().equals(door.to())) {
                    invalid.add(issue(at, "doors[%d]".formatted(i), "a door has two jambs, not one point"));
                } else if (!Shape.onOutline(points, door.from(), door.to())) {
                    invalid.add(issue(at, "doors[%d]".formatted(i), "a door of '%s' is not on its outline"
                            .formatted(space.code())));
                }
            }
        }
    }

    /**
     * {@code accessVia} has to name a lift, a staircase or an entrance of the same building - in
     * this layout or on another floor - and not the space itself. It is what produces "sube por
     * el ascensor central", and pointing a classroom at another classroom would produce an
     * instruction nobody can follow.
     */
    private static void checkAccessVia(BuildingDocument building, LayoutSpaceDto space, String at,
                                       Map<String, LayoutSpaceDto> sameFloor,
                                       Map<String, SpaceDocument> otherFloors,
                                       Map<String, SpaceTypeDocument> types,
                                       List<ApiError.FieldIssue> invalid) {
        String target = space.accessViaOrNull();
        if (target == null) {
            return;
        }
        if (target.equals(space.code())) {
            invalid.add(issue(at, "accessVia", "a space cannot be reached through itself"));
            return;
        }
        String targetType;
        if (sameFloor.containsKey(target)) {
            targetType = sameFloor.get(target).typeCode();
        } else if (otherFloors.containsKey(target)) {
            targetType = otherFloors.get(target).typeCode();
        } else {
            invalid.add(issue(at, "accessVia", "'%s' is not a space in building %s"
                    .formatted(target, building.code())));
            return;
        }
        SpaceTypeDocument type = types.get(targetType);
        if (type == null || type.category() != SpaceCategory.CIRCULATION) {
            invalid.add(issue(at, "accessVia",
                    "'%s' is not a lift, a staircase or an entrance, so nobody travels through it"
                            .formatted(target)));
        }
    }

    private static boolean overlap(LayoutSpaceDto a, LayoutSpaceDto b) {
        return a.shape() != null && b.shape() != null
                && Shape.overlap(MapMapper.toPoints(a.shape()), MapMapper.toPoints(b.shape()));
    }

    private static ApiError.FieldIssue issue(String at, String field, String message) {
        return new ApiError.FieldIssue(at.isEmpty() ? field : at + "." + field, message);
    }

    /** The shape the rules check, from a stored space. */
    static LayoutSpaceDto asLayout(SpaceDocument space) {
        return new LayoutSpaceDto(space.code(), space.doorCode(), space.wing(), space.name(),
                space.typeCode(), space.aliases(), MapMapper.toPointDtos(space.shape()),
                MapMapper.toDoorDtos(space.doors()), space.accessVia(),
                space.accessibility(), space.note(), space.capacity());
    }

    static boolean sameCode(LayoutSpaceDto a, SpaceDocument b) {
        return Objects.equals(a.code(), b.code());
    }
}
