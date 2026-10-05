package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@code V004} and {@code V006} do to documents an older model wrote: the shared cluster
 * holds them, and they have to come out readable - and, for V006, not marked as edited.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        // The placeholder campus is these tests' fixture; SurveyedCampusTest covers the survey.
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class LegacyConversionTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private BuildingRepository buildings;
    @Autowired
    private SpaceRepository spaces;

    @Test
    @DisplayName("an old-shape building and its spaces are converted and read back through the model")
    void legacyDocumentsAreConverted() {
        mongo.getCollection("buildings").insertOne(new Document("_id", "legacy-b")
                .append("code", "OLD").append("name", "Edificio viejo").append("campus", "Sede Test")
                .append("placeholder", false)
                .append("floors", List.of(
                        new Document("level", -1).append("name", "Sótano").append("gridRows", 5)
                                .append("gridColumns", 5).append("corridors", List.of()),
                        new Document("level", 3).append("name", "Piso 3").append("gridRows", 5)
                                .append("gridColumns", 5).append("corridors", List.of()))));
        mongo.getCollection("spaces").insertMany(List.of(
                new Document("_id", "legacy-s1").append("code", "301-S").append("baseCode", "301")
                        .append("wing", "SUR").append("name", "Aula 301 Sur").append("type", "CLASSROOM")
                        .append("buildingId", "legacy-b").append("buildingCode", "OLD").append("campus", "Sede Test")
                        .append("floorLevel", 3).append("aliases", List.of()).append("gridRow", 1)
                        .append("gridColumn", 1).append("rowSpan", 1).append("colSpan", 1).append("placeholder", false),
                new Document("_id", "legacy-s2").append("code", "ASC-1").append("baseCode", "ASC-1")
                        .append("name", "Ascensor").append("type", "ELEVATOR")
                        .append("buildingId", "legacy-b").append("buildingCode", "OLD").append("campus", "Sede Test")
                        .append("floorLevel", -1).append("aliases", List.of()).append("gridRow", 0)
                        .append("gridColumn", 0).append("rowSpan", 1).append("colSpan", 1).append("placeholder", false)));

        assertThat(V004_MapModelV2.convertLegacy(mongo)).isEqualTo(1);
        // V006 follows it everywhere: the model reads rooms as shapes, not grid cells.
        V006_PolygonShapes.convert(mongo);

        BuildingDocument building = buildings.findByCode("OLD").orElseThrow();
        assertThat(building.floors()).extracting(f -> f.code()).containsExactly("S1", "P3");
        assertThat(building.wings()).extracting(w -> w.code()).containsExactly("S");

        SpaceDocument room = spaces.findById("legacy-s1").orElseThrow();
        assertThat(room.typeCode()).isEqualTo("CLASSROOM");
        assertThat(room.floorCode()).isEqualTo("P3");
        assertThat(room.wing()).isEqualTo("S");
        assertThat(room.doorCode()).isEqualTo("301-S");

        SpaceDocument lift = spaces.findById("legacy-s2").orElseThrow();
        assertThat(lift.floorCode()).isEqualTo("S1");
        assertThat(lift.doorCode()).as("a lift's old code was an identifier, not a door number").isNull();

        assertThat(V004_MapModelV2.convertLegacy(mongo)).as("running it again finds nothing to do").isZero();
        assertThat(spaces.findById("legacy-s1").orElseThrow().shape())
                .containsExactly(new Point(40, 40), new Point(80, 40), new Point(80, 80), new Point(40, 80));
        assertThat(buildings.findByCode("OLD").orElseThrow().floors()).extracting(Floor::width).containsOnly(200);
    }

    @Test
    @DisplayName("a floor drawn on the grid keeps every room where it was, now in units, and is not marked as edited")
    void gridFloorsBecomePolygons() {
        Date created = Date.from(Instant.parse("2026-09-23T10:00:00Z"));
        mongo.getCollection("buildings").insertOne(new Document("_id", "grid-b")
                .append("code", "GRD").append("name", "Dibujado en rejilla").append("campus", "Sede Test")
                .append("placeholder", false).append("createdAt", created).append("updatedAt", created)
                .append("floors", List.of(new Document("code", "P1").append("level", 1.0).append("name", "Piso 1")
                        .append("status", "DRAFT").append("accessibility", "UNKNOWN")
                        .append("gridRows", 6).append("gridColumns", 10).append("version", 0L)
                        .append("corridors", List.of(new Document("code", "PAS").append("name", "Pasillo")
                                .append("color", "#5B8DEF").append("path", List.of(
                                        new Document("row", 3).append("col", 0),
                                        new Document("row", 3).append("col", 9))))))));
        mongo.getCollection("spaces").insertMany(List.of(
                new Document("_id", "grid-s1").append("code", "101").append("doorCode", "101")
                        .append("name", "Aula 101").append("typeCode", "CLASSROOM").append("buildingId", "grid-b")
                        .append("buildingCode", "GRD").append("campus", "Sede Test").append("floorCode", "P1")
                        .append("floorLevel", 1.0).append("aliases", List.of()).append("gridRow", 1)
                        .append("gridColumn", 2).append("rowSpan", 2).append("colSpan", 3)
                        .append("placeholder", false).append("createdAt", created).append("updatedAt", created),
                new Document("_id", "grid-s2").append("code", "GRD-DEP").append("name", "Sin ubicar")
                        .append("typeCode", "OFFICE").append("buildingId", "grid-b").append("buildingCode", "GRD")
                        .append("campus", "Sede Test").append("floorCode", "P1").append("floorLevel", 1.0)
                        .append("aliases", List.of()).append("gridRow", null).append("gridColumn", null)
                        .append("rowSpan", 1).append("colSpan", 1).append("placeholder", false)
                        .append("createdAt", created).append("updatedAt", created)));

        V006_PolygonShapes.Result result = V006_PolygonShapes.convert(mongo);

        assertThat(result.floors()).isEqualTo(1);
        Floor floor = buildings.findByCode("GRD").orElseThrow().floor("P1").orElseThrow();
        assertThat(floor.width()).isEqualTo(400);
        assertThat(floor.height()).isEqualTo(240);
        assertThat(floor.version()).as("a conversion is not an edit").isZero();
        assertThat(floor.corridors().getFirst().path()).containsExactly(new Point(20, 140), new Point(380, 140));

        SpaceDocument room = spaces.findById("grid-s1").orElseThrow();
        assertThat(room.shape()).containsExactly(
                new Point(80, 40), new Point(200, 40), new Point(200, 120), new Point(80, 120));
        assertThat(room.updatedAt()).as("a conversion is not an edit").isEqualTo(room.createdAt());
        assertThat(spaces.findById("grid-s2").orElseThrow().isPlaced()).isFalse();
        assertThat(mongo.getCollection("spaces").find(new Document("rowSpan", new Document("$exists", true))).first())
                .as("no grid field is left behind").isNull();

        assertThat(V006_PolygonShapes.convert(mongo)).as("running it again finds nothing to do")
                .isEqualTo(new V006_PolygonShapes.Result(0, 0));
    }
}
