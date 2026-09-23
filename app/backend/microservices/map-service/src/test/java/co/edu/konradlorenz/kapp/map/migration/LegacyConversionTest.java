package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@code V004} does to a building somebody entered through the portal before the model
 * changed: the shared cluster may hold one, and it has to come out readable.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
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
    }
}
