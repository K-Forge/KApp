package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
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
                null, 10, 10, List.of(), 0);
    }

    public static BuildingDocument building(String code, List<Wing> wings, Floor... floors) {
        Instant now = Instant.now();
        return new BuildingDocument(UUID.randomUUID().toString(), code, "Fixture " + code,
                "Sede Test", null, List.of(), wings, List.of(floors), false, now, now);
    }

    public static BuildingDocument building(String code) {
        return building(code, List.of(), floor("P1", 1));
    }

    /** A placed office on the building's floor, with its code as its door code. */
    public static SpaceDocument space(BuildingDocument building, String code, String floorCode,
                                      int gridRow, int gridColumn) {
        Instant now = Instant.now();
        Floor floor = building.floor(floorCode).orElseThrow();
        return new SpaceDocument(UUID.randomUUID().toString(), code, code,
                SpaceDocument.baseCodeOf(code, building.wings()), null, "Fixture space " + code,
                "OFFICE", building.id(), building.code(), building.campus(), floorCode,
                floor.level(), List.of(), gridRow, gridColumn, 1, 1, null, null, null, null,
                false, now, now);
    }
}
