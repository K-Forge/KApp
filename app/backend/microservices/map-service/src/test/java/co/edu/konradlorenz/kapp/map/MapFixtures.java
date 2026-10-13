package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.Wing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Buildings, floors and spaces for tests that need one the seed does not have. One place builds
 * them, so a field added to the model is added here once rather than in every test.
 */
public final class MapFixtures {

    private MapFixtures() {
    }

    public static Floor floor(String code, double level) {
        return new Floor(code, level, "Piso " + code, FloorStatus.DRAFT, Accessibility.UNKNOWN,
                null, 400, 400, List.of(), List.of(), 0);
    }

    public static BuildingDocument building(String code, List<Wing> wings, Floor... floors) {
        Instant now = Instant.now();
        return new BuildingDocument(UUID.randomUUID().toString(), code, "Fixture " + code,
                "Sede Test", null, List.of(), wings, List.of(floors), false, now, now);
    }

    public static BuildingDocument building(String code) {
        return building(code, List.of(), floor("P1", 1));
    }

    /** A square {@code size} units across with its top left corner at (x, y). */
    public static List<Point> square(int x, int y, int size) {
        return List.of(new Point(x, y), new Point(x + size, y), new Point(x + size, y + size),
                new Point(x, y + size));
    }

    /**
     * A placed office on the building's floor, with its code as its door code: a square 40 units
     * across at (x, y).
     */
    public static SpaceDocument space(BuildingDocument building, String code, String floorCode, int x, int y) {
        Instant now = Instant.now();
        Floor floor = building.floor(floorCode).orElseThrow();
        return new SpaceDocument(UUID.randomUUID().toString(), code, code,
                SpaceDocument.baseCodeOf(code, building.wings()), null, "Fixture space " + code,
                "OFFICE", building.id(), building.code(), building.campus(), floorCode,
                floor.level(), List.of(), square(x, y, 40), List.of(), null, null, null, null,
                false, now, now);
    }
}
