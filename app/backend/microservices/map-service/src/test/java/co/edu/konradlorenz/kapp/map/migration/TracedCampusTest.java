package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.CampusStructuresDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.Structure;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import co.edu.konradlorenz.kapp.map.web.dto.FloorLayoutRequest;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.PointDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The traced campus, loaded the way a real database gets it: every change unit in order, with
 * {@link V007_TracedCampus} switched on.
 *
 * <p>Nothing here names a room. The snapshot is rewritten every time a floor is traced or the
 * campus is exported from the portal, so what is asserted is what must hold for any snapshot:
 * that it loads whole, that the placeholders are gone, that every floor would be accepted by the
 * floor editor, and that a floor somebody worked on is never overwritten.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class TracedCampusTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final List<SurveySnapshot.Building> SNAPSHOT = SurveySnapshot.load();

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MongoClient client;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    private static RequestPostProcessor admin() {
        return jwt()
                .jwt(b -> b.subject("survey-admin").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("the snapshot is there to load, and names each building once")
    void snapshotIsPresent() {
        assertThat(SNAPSHOT).isNotEmpty();
        assertThat(SNAPSHOT).extracting(SurveySnapshot.Building::code).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the Edificio Central is laid on the ground: its drawing's top faces the Carrera 9 Bis")
    void theCentralBuildingIsOnTheGround() {
        BuildingDocument central = mongo.findOne(query(where("code").is("EC")), BuildingDocument.class);
        assertThat(central.placement()).isNotNull();
        // South-east, between the quarter the floors' top records (EAST) and the next one.
        assertThat(central.placement().bearing()).isBetween(90.0, 180.0);
        assertThat(central.placement().origin().lat()).isBetween(4.64, 4.66);
        assertThat(central.placement().origin().lon()).isBetween(-74.07, -74.05);
        // Its three wings, told apart from above: the five floors along the Calle 63 are the north
        // wing, the eight-floor core the central one.
        assertThat(central.footprint()).anyMatch(p -> p.floors() == 5 && "N".equals(p.wing()));
        assertThat(central.footprint()).anyMatch(p -> p.floors() == 8 && "C".equals(p.wing()));
        assertThat(central.footprint()).anyMatch(p -> "S".equals(p.wing()));
    }

    @Test
    @DisplayName("every building the survey placed is on the ground, the ones nobody has drawn yet included, and one it has no place for is not")
    void everyBuildingIsOnTheGround() {
        for (SurveySnapshot.Building surveyed : SNAPSHOT) {
            BuildingDocument stored = mongo.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
            if (surveyed.toPlacement() == null) {
                // RH: the heritage house it stood on is no office, and where it is is still asked.
                assertThat(stored.placement()).as(surveyed.code()).isNull();
                assertThat(stored.footprint()).as(surveyed.code()).isEmpty();
                continue;
            }
            assertThat(stored.placement()).as(surveyed.code()).isNotNull();
            assertThat(stored.footprint()).as(surveyed.code()).isNotEmpty();
        }
        assertThat(SNAPSHOT).filteredOn(b -> b.toPlacement() != null).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("the placeholder campus is gone: no placeholder building, no placeholder space")
    void placeholdersAreGone() {
        assertThat(mongo.count(query(where("placeholder").is(true)), BuildingDocument.class)).isZero();
        assertThat(mongo.count(query(where("placeholder").is(true)), SpaceDocument.class)).isZero();
    }

    @Test
    @DisplayName("every surveyed building is stored whole: its floors, and each space with the shape and doors the snapshot draws")
    void everyBuildingIsLoadedWhole() {
        for (SurveySnapshot.Building surveyed : SNAPSHOT) {
            BuildingDocument stored = mongo.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
            assertThat(stored).as(surveyed.code()).isNotNull();
            assertThat(stored.placeholder()).isFalse();
            assertThat(stored.aliases()).containsExactlyElementsOf(surveyed.aliases());
            assertThat(stored.placement()).as(surveyed.code() + " on the ground").isEqualTo(surveyed.toPlacement());
            assertThat(stored.footprint()).as(surveyed.code() + " from above").isEqualTo(surveyed.toFootprint());
            assertThat(stored.floors()).extracting(Floor::code)
                    .containsExactlyInAnyOrderElementsOf(surveyed.floors().stream().map(SurveySnapshot.SnapshotFloor::code).toList());

            for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
                Floor storedFloor = stored.floor(floor.code()).orElseThrow();
                assertThat(storedFloor.width()).as(surveyed.code() + " " + floor.code()).isEqualTo(floor.width());
                assertThat(storedFloor.version()).as("loading is not an edit").isZero();

                List<SpaceDocument> spaces = mongo.find(query(where("buildingId").is(stored.id())
                        .and("floorCode").is(floor.code())), SpaceDocument.class);
                assertThat(spaces).as(surveyed.code() + " " + floor.code())
                        .extracting(SpaceDocument::code)
                        .containsExactlyInAnyOrderElementsOf(floor.spaces().stream().map(LayoutSpaceDto::code).toList());
                for (LayoutSpaceDto space : floor.spaces()) {
                    SpaceDocument match = spaces.stream().filter(s -> s.code().equals(space.code())).findFirst().orElseThrow();
                    assertThat(match.shape()).as(space.code()).isEqualTo(MapMapper.toPoints(space.shape()));
                    assertThat(match.doors()).as(space.code()).isEqualTo(MapMapper.toDoors(space.doorsOrEmpty()));
                }
            }
        }
    }

    @Test
    @DisplayName("every surveyed floor is one the floor editor would save back unchanged")
    void everyFloorSavesThroughTheEditor() throws Exception {
        // The same endpoint, validation and rules the portal saves through - so a snapshot the
        // editor would refuse fails here, not on somebody's iPad.
        for (SurveySnapshot.Building surveyed : SNAPSHOT) {
            for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
                String path = "/api/map/buildings/%s/floors/%s".formatted(surveyed.code(), floor.code());
                JsonNode detail = json.readTree(mockMvc.perform(get(path).with(admin()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
                FloorLayoutRequest request = new FloorLayoutRequest(detail.get("version").asLong(),
                        floor.width(), floor.height(), floor.top(), floor.outline(), floor.status(), floor.accessibility(),
                        floor.note(), floor.corridors(), floor.spaces());

                mockMvc.perform(put(path + "/layout").with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(request)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    @DisplayName("every other name in the snapshot finds its space")
    void aliasesAreSearchable() throws Exception {
        for (SurveySnapshot.Building surveyed : SNAPSHOT) {
            LayoutSpaceDto named = surveyed.floors().stream().flatMap(f -> f.spaces().stream())
                    .filter(s -> !s.aliasesOrEmpty().isEmpty()).findFirst().orElse(null);
            if (named == null) {
                continue;
            }
            String alias = named.aliasesOrEmpty().getFirst();
            JsonNode page = json.readTree(mockMvc.perform(get("/api/map/spaces/search")
                            .param("q", alias).param("buildingCode", surveyed.code()).param("size", "100")
                            .with(admin()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(page.get("content").findValuesAsText("code")).as("'%s' in %s", alias, surveyed.code())
                    .contains(named.code());
        }
    }

    @Test
    @DisplayName("a floor somebody worked on is kept, an untouched one is redrawn keeping its spaces' ids, and a placeholder holding real spaces stays")
    void somebodysWorkIsKept() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_rerun_" + UUID.randomUUID().toString().substring(0, 8));
        Instant then = Instant.parse("2026-09-20T10:00:00Z");
        SurveySnapshot.Building worked = SNAPSHOT.stream()
                .filter(b -> !b.floors().isEmpty()).findFirst().orElseThrow();
        SurveySnapshot.Building untouched = SNAPSHOT.stream()
                .filter(b -> b != worked && b.floors().stream().anyMatch(f -> !f.spaces().isEmpty()))
                .findFirst().orElseThrow();
        SurveySnapshot.SnapshotFloor workedFloor = worked.floors().getFirst();
        SurveySnapshot.SnapshotFloor redrawn = untouched.floors().stream()
                .filter(f -> !f.spaces().isEmpty()).findFirst().orElseThrow();
        String kept = redrawn.spaces().getFirst().code();

        // Somebody saved a layout on this floor: version 3.
        fresh.insert(building(worked.code(), workedFloor.code(), 3, false, then));
        // Nobody touched this one, and one of its spaces is already there under its code.
        BuildingDocument drafted = fresh.insert(building(untouched.code(), redrawn.code(), 0, false, then));
        SpaceDocument before = fresh.insert(space(drafted, kept, redrawn.code(), false, then));
        BuildingDocument inUse = fresh.insert(building("A", "P1", 0, true, then));
        BuildingDocument empty = fresh.insert(building("B", "P1", 0, true, then));
        fresh.insert(space(inUse, "301", "P1", false, then));
        fresh.insert(space(empty, "101", "P1", true, then));

        V007_TracedCampus.Result result = V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.now());

        assertThat(result.kept()).containsExactly(worked.code() + " " + workedFloor.code());
        Floor workedStored = fresh.findOne(query(where("code").is(worked.code())), BuildingDocument.class)
                .floor(workedFloor.code()).orElseThrow();
        assertThat(workedStored.width()).as("the worked-on floor keeps its own drawing").isEqualTo(400);
        assertThat(workedStored.version()).isEqualTo(3);

        Floor redrawnStored = fresh.findOne(query(where("code").is(untouched.code())), BuildingDocument.class)
                .floor(redrawn.code()).orElseThrow();
        assertThat(redrawnStored.width()).isEqualTo(redrawn.width());
        SpaceDocument after = fresh.findOne(query(where("buildingId").is(drafted.id()).and("code").is(kept)),
                SpaceDocument.class);
        assertThat(after.id()).as("the snapshot's version of a room is the same room").isEqualTo(before.id());
        assertThat(after.updatedAt()).as("redrawing is not an edit").isEqualTo(after.createdAt());

        assertThat(result.placeholdersInUse()).containsExactly("A");
        assertThat(fresh.exists(query(where("code").is("B")), BuildingDocument.class)).isFalse();
        assertThat(result.placeholderSpacesRemoved()).isEqualTo(1);
        assertThat(result.buildingsAdded()).isEqualTo(SNAPSHOT.size() - 2);
    }

    @Test
    @DisplayName("a space somebody added through the spaces screen stays when its floor is loaded again")
    void aSpaceAddedLaterKeepsItsFloor() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_added_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.parse("2026-09-20T10:00:00Z"));
        SurveySnapshot.Building surveyed = SNAPSHOT.getFirst();
        BuildingDocument stored = fresh.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
        String floor = surveyed.floors().getFirst().code();
        // Created and never edited, like every space a load writes - but a day after the load.
        Instant later = Instant.parse("2026-09-21T10:00:00Z");
        fresh.insert(new SpaceDocument(UUID.randomUUID().toString(), "ADDED-1", null, null, null, "Añadido a mano",
                "OFFICE", stored.id(), stored.code(), stored.campus(), floor, 1, List.of(), null, List.of(),
                null, null, null, null, false, later, later));

        V007_TracedCampus.Result again = V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.now());

        // The load knows what it wrote, so it redraws the floor and carries the space over.
        assertThat(again.kept()).isEmpty();
        assertThat(again.merged()).singleElement().satisfies(m -> assertThat(m).startsWith(surveyed.code() + " " + floor));
        assertThat(fresh.exists(query(where("code").is("ADDED-1")), SpaceDocument.class)).isTrue();
    }

    @Test
    @DisplayName("run twice, it writes the same campus twice: what it wrote is still untouched")
    void runningTwiceChangesNothing() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_twice_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.now());
        long spaces = fresh.count(new Query(), SpaceDocument.class);

        V007_TracedCampus.Result again = V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.now());

        assertThat(again.kept()).as("nothing it wrote counts as somebody's work").isEmpty();
        assertThat(again.buildingsAdded()).isZero();
        assertThat(fresh.count(new Query(), SpaceDocument.class)).isEqualTo(spaces);
    }

    @Test
    @DisplayName("the first floor of the Edificio Central is redrawn over somebody's work, which is kept aside whole")
    void theCentralGroundFloorIsRedrawnAndTheOldOneKept() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_redraw_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.parse("2026-09-20T10:00:00Z"));
        BuildingDocument central = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class);
        // Somebody saved a layout on it, and added a space of their own.
        fresh.getCollection("buildings").updateOne(
                new Document("_id", central.id()).append("floors.code", "P1"),
                new Document("$set", new Document("floors.$.version", 4L)));
        Instant later = Instant.parse("2026-09-22T10:00:00Z");
        fresh.insert(new SpaceDocument(UUID.randomUUID().toString(), "MINE-1", null, null, null, "Hecho a mano",
                "OFFICE", central.id(), central.code(), central.campus(), "P1", 1, List.of(), null, List.of(),
                null, null, null, null, false, later, later));
        long otherFloors = fresh.count(query(where("buildingId").is(central.id()).and("floorCode").ne("P1")),
                SpaceDocument.class);

        int keptAside = V008_RedrawnCentralGroundFloor.redraw(fresh, SNAPSHOT, "EC", "P1", Instant.now());

        SurveySnapshot.SnapshotFloor surveyed = SNAPSHOT.stream().filter(b -> b.code().equals("EC")).findFirst()
                .orElseThrow().floors().stream().filter(f -> f.code().equals("P1")).findFirst().orElseThrow();
        Floor redrawn = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class).floor("P1").orElseThrow();
        assertThat(redrawn.version()).as("as a load leaves it").isZero();
        assertThat(redrawn.width()).isEqualTo(surveyed.width());
        assertThat(fresh.exists(query(where("code").is("MINE-1")), SpaceDocument.class)).isFalse();
        assertThat(fresh.count(query(where("buildingId").is(central.id()).and("floorCode").is("P1")),
                SpaceDocument.class)).isEqualTo(surveyed.spaces().size());
        assertThat(fresh.count(query(where("buildingId").is(central.id()).and("floorCode").ne("P1")),
                SpaceDocument.class)).as("the other floors are left alone").isEqualTo(otherFloors);

        Document old = fresh.getCollection(V008_RedrawnCentralGroundFloor.REPLACED).find().first();
        assertThat(old).isNotNull();
        assertThat(old.get("floor", Document.class).getLong("version")).isEqualTo(4L);
        assertThat(old.getList("spaces", Document.class)).hasSize(keptAside)
                .anyMatch(s -> "MINE-1".equals(s.getString("code")));
        assertThat(V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.now()).kept())
                .as("the redrawn floor is one a load wrote").isEmpty();
    }

    @Test
    @DisplayName("a floor somebody named is redrawn with their names on the new drawing, and kept aside whole first")
    void namesGoOntoTheRedrawnFloor() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_merge_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.parse("2026-09-20T10:00:00Z"));
        SurveySnapshot.Building ec = SNAPSHOT.stream().filter(b -> b.code().equals("EC")).findFirst().orElseThrow();
        SurveySnapshot.SnapshotFloor p1 = ec.floors().stream().filter(f -> f.code().equals("P1")).findFirst().orElseThrow();
        LayoutSpaceDto box = p1.spaces().stream()
                .filter(sp -> "OTHER".equals(sp.typeCode()) && sp.shape() != null && sp.shape().size() == 4)
                .findFirst().orElseThrow();
        LayoutSpaceDto cafeteria = p1.spaces().stream().filter(sp -> sp.shape() == null).findFirst().orElseThrow();
        BuildingDocument central = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class);

        // In the editor: the box given to the inventoried space, and the floor saved.
        Query onP1 = query(where("buildingId").is(central.id()).and("floorCode").is("P1"));
        SpaceDocument given = fresh.findOne(query(where("buildingId").is(central.id()).and("code").is(box.code())),
                SpaceDocument.class);
        fresh.updateFirst(query(where("buildingId").is(central.id()).and("code").is(cafeteria.code())),
                new Update().set("shape", given.shape())
                        .set("updatedAt", Instant.parse("2026-09-21T10:00:00Z")),
                SpaceDocument.class);
        fresh.remove(given);
        fresh.getCollection("buildings").updateOne(
                new Document("_id", central.id()).append("floors.code", "P1"),
                new Document("$set", new Document("floors.$.version", 2L).append("updatedAt", new Date())));
        long before = fresh.count(onP1, SpaceDocument.class);

        // The snapshot then draws that box 4 units in from each side.
        List<PointDto> shrunk = inset(box.shape(), 4);
        SurveySnapshot.Building redrawn = withFloor(ec, new SurveySnapshot.SnapshotFloor(p1.code(), p1.level(), p1.name(),
                p1.status(), p1.accessibility(), p1.note(), p1.width(), p1.height(), p1.top(), p1.outline(),
                p1.corridors(), p1.spaces().stream().map(sp -> sp.code().equals(box.code()) ? new LayoutSpaceDto(
                        sp.code(), sp.doorCode(), sp.wing(), sp.name(), sp.typeCode(), sp.aliases(), shrunk, List.of(),
                        sp.accessVia(), sp.accessibility(), sp.note(), sp.capacity()) : sp).toList()));

        V007_TracedCampus.Result result = V007_TracedCampus.apply(fresh, List.of(redrawn), Instant.now());

        assertThat(result.kept()).isEmpty();
        assertThat(result.merged()).singleElement().satisfies(m -> assertThat(m).startsWith("EC P1"));
        SpaceDocument named = fresh.findOne(query(where("buildingId").is(central.id()).and("code").is(cafeteria.code())),
                SpaceDocument.class);
        assertThat(named.shape()).isEqualTo(MapMapper.toPoints(shrunk));
        assertThat(fresh.exists(query(where("buildingId").is(central.id()).and("code").is(box.code())),
                SpaceDocument.class)).isFalse();
        assertThat(fresh.count(onP1, SpaceDocument.class)).isEqualTo(before);
        Floor p1Stored = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class).floor("P1").orElseThrow();
        assertThat(p1Stored.version()).as("still somebody's work, so the next load merges again").isEqualTo(3);
        assertThat(fresh.getCollection(V008_RedrawnCentralGroundFloor.REPLACED).countDocuments()).isEqualTo(1);
    }

    @Test
    @DisplayName("saving a floor does not stop the building's footprint from being redrawn")
    void aSavedFloorDoesNotKeepTheOldFootprint() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_base_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.parse("2026-09-20T10:00:00Z"));
        SurveySnapshot.Building ec = SNAPSHOT.stream().filter(b -> b.code().equals("EC")).findFirst().orElseThrow();
        // A layout saved: the building's updatedAt moves, as the floor editor's save does.
        fresh.getCollection("buildings").updateOne(new Document("code", "EC"),
                new Document("$set", new Document("updatedAt", new Date())));
        SurveySnapshot.Building fewerParts = new SurveySnapshot.Building(ec.code(), ec.name(), ec.campus(),
                ec.description(), ec.aliases(), ec.wings(), ec.floors(), ec.placement(),
                ec.footprint().subList(0, ec.footprint().size() - 1));

        V007_TracedCampus.apply(fresh, List.of(fewerParts), Instant.now());

        assertThat(fresh.findOne(query(where("code").is("EC")), BuildingDocument.class).footprint())
                .isEqualTo(fewerParts.toFootprint());
    }

    @Test
    @DisplayName("the Edificio Central is refitted once: its first and third floors redrawn over the names, kept aside, and its footprint taken")
    void theCentralBuildingIsRefitted() {
        MongoTemplate fresh = new MongoTemplate(client, "traced_v009_" + UUID.randomUUID().toString().substring(0, 8));
        V007_TracedCampus.apply(fresh, SNAPSHOT, Instant.parse("2026-09-20T10:00:00Z"));
        BuildingDocument central = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class);
        for (String floor : V009_FittedCentralBuilding.FLOORS) {
            fresh.getCollection("buildings").updateOne(new Document("_id", central.id()).append("floors.code", floor),
                    new Document("$set", new Document("floors.$.version", 5L)));
        }
        fresh.getCollection("buildings").updateOne(new Document("_id", central.id()),
                new Document("$set", new Document("footprint", List.of()).append("updatedAt", new Date())));
        fresh.getCollection(SnapshotBases.COLLECTION).deleteMany(new Document());

        V009_FittedCentralBuilding.apply(fresh, SNAPSHOT, Instant.now());

        SurveySnapshot.Building ec = SNAPSHOT.stream().filter(b -> b.code().equals("EC")).findFirst().orElseThrow();
        BuildingDocument after = fresh.findOne(query(where("code").is("EC")), BuildingDocument.class);
        assertThat(after.footprint()).isEqualTo(ec.toFootprint());
        for (String floor : V009_FittedCentralBuilding.FLOORS) {
            assertThat(after.floor(floor).orElseThrow().version()).as(floor + ", as a load leaves it").isZero();
        }
        assertThat(fresh.getCollection(V008_RedrawnCentralGroundFloor.REPLACED).countDocuments()).isEqualTo(2);
        assertThat(SnapshotBases.find(fresh, "EC").orElseThrow().floors()).hasSize(ec.floors().size());
    }

    private static SurveySnapshot.Building withFloor(SurveySnapshot.Building building, SurveySnapshot.SnapshotFloor floor) {
        return new SurveySnapshot.Building(building.code(), building.name(), building.campus(), building.description(),
                building.aliases(), building.wings(),
                building.floors().stream().map(f -> f.code().equals(floor.code()) ? floor : f).toList(),
                building.placement(), building.footprint());
    }

    @Test
    @DisplayName("the campus's structures are put on once, and a list somebody saved since is kept")
    void structuresAreSeededOnce() {
        CampusStructuresDocument seeded = mongo.findById("sede principal", CampusStructuresDocument.class);
        assertThat(seeded).as("the change units have run").isNotNull();
        assertThat(seeded.structures()).extracting(Structure::name)
                .contains("Casa de Francisco de Paula Vélez", "Vecino, Calle 62 # 9-46 a 9-60");
        assertThat(seeded.structures()).allSatisfy(s -> assertThat(s.ring().getFirst()).isEqualTo(s.ring().getLast()));

        mongo.save(new CampusStructuresDocument(seeded.id(), seeded.campus(),
                List.of(seeded.structures().getFirst()), seeded.version() + 1, Instant.now()));
        assertThat(V010_CampusStructures.seed(mongo, V010_CampusStructures.load(), Instant.now())).isEmpty();
        assertThat(mongo.findById("sede principal", CampusStructuresDocument.class).structures()).hasSize(1);

        mongo.save(seeded);
    }

    /** A four-cornered outline pulled in by `by` on every side. */
    private static List<PointDto> inset(
            List<PointDto> shape, int by) {
        int x0 = shape.stream().mapToInt(PointDto::x).min().orElseThrow() + by;
        int x1 = shape.stream().mapToInt(PointDto::x).max().orElseThrow() - by;
        int y0 = shape.stream().mapToInt(PointDto::y).min().orElseThrow() + by;
        int y1 = shape.stream().mapToInt(PointDto::y).max().orElseThrow() - by;
        return List.of(new PointDto(x0, y0),
                new PointDto(x1, y0),
                new PointDto(x1, y1),
                new PointDto(x0, y1));
    }

    private static BuildingDocument building(String code, String floor, long version, boolean placeholder,
                                             Instant then) {
        return new BuildingDocument(UUID.randomUUID().toString(), code, "Hecho a mano", "Sede Principal", null,
                List.of(), List.of(),
                List.of(new Floor(floor, 1, "Piso", FloorStatus.DRAFT, Accessibility.UNKNOWN, null, 400, 240,
                        List.of(), List.of(), version)),
                placeholder, then, then);
    }

    private static SpaceDocument space(BuildingDocument building, String code, String floor, boolean placeholder,
                                       Instant then) {
        return new SpaceDocument(UUID.randomUUID().toString(), code, code, code, null, "Aula " + code,
                "CLASSROOM", building.id(), building.code(), building.campus(), floor, 1, List.of(),
                List.of(new Point(0, 0), new Point(40, 0), new Point(40, 40), new Point(0, 40)), List.of(),
                null, null, null, null, placeholder, then, then);
    }

    @Test
    @DisplayName("the casa is redrawn on its wall where it is still the cadastre's, and left alone where somebody corrected it")
    void theCasaIsRedrawnOnlyWhereNobodyTouchedIt() {
        String id = co.edu.konradlorenz.kapp.map.service.GroundService.key("Sede Principal");
        CampusStructuresDocument now = mongo.findById(id, CampusStructuresDocument.class);
        Structure seeded = now.structures().stream().filter(s -> V012_CasaOnItsWall.CASA_LOT.equals(s.lot())).findFirst().orElseThrow();
        // A fresh database gets the seed's casa, on its wall with its door and balcony, straight away.
        assertThat(seeded.ring()).isNotEqualTo(V012_CasaOnItsWall.SEEDED).isNotEqualTo(V013_CasaDoorAndBalcony.FROM_V012)
                .isNotEqualTo(V014_CasaFromTheSurvey.FROM_V013).hasSize(10);

        // One V010 seeded before: redrawn, the version moved on, the other structures kept.
        List<Structure> cadastre = now.structures().stream()
                .map(s -> V012_CasaOnItsWall.CASA_LOT.equals(s.lot()) ? new Structure(s.name(), s.floors(), s.basements(), s.lot(), V012_CasaOnItsWall.SEEDED) : s)
                .toList();
        mongo.save(new CampusStructuresDocument(id, now.campus(), cadastre, 7, Instant.now()));
        assertThat(V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now())).isEqualTo(1);
        CampusStructuresDocument redrawn = mongo.findById(id, CampusStructuresDocument.class);
        assertThat(redrawn.version()).isEqualTo(8);
        assertThat(redrawn.structures()).hasSameSizeAs(cadastre);
        assertThat(redrawn.structures()).contains(seeded);

        // One V012 left, a straight front: given its door and balcony too.
        List<Structure> straight = now.structures().stream()
                .map(s -> V012_CasaOnItsWall.CASA_LOT.equals(s.lot()) ? new Structure(s.name(), s.floors(), s.basements(), s.lot(), V013_CasaDoorAndBalcony.FROM_V012) : s)
                .toList();
        mongo.save(new CampusStructuresDocument(id, now.campus(), straight, 8, Instant.now()));
        assertThat(V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now(), V013_CasaDoorAndBalcony.FROM_V012)).isEqualTo(1);
        assertThat(mongo.findById(id, CampusStructuresDocument.class).structures()).contains(seeded);

        // And one V013 left, with the corner too far north: moved to where the survey has it.
        List<Structure> v013 = now.structures().stream()
                .map(s -> V012_CasaOnItsWall.CASA_LOT.equals(s.lot()) ? new Structure(s.name(), s.floors(), s.basements(), s.lot(), V014_CasaFromTheSurvey.FROM_V013) : s)
                .toList();
        mongo.save(new CampusStructuresDocument(id, now.campus(), v013, 8, Instant.now()));
        assertThat(V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now(), V014_CasaFromTheSurvey.FROM_V013)).isEqualTo(1);
        assertThat(mongo.findById(id, CampusStructuresDocument.class).structures()).contains(seeded);

        // One somebody corrected in the portal: theirs.
        List<List<Double>> corrected = List.of(List.of(-74.0619, 4.6484), List.of(-74.0618, 4.6485), List.of(-74.0617, 4.6484), List.of(-74.0619, 4.6484));
        List<Structure> edited = now.structures().stream()
                .map(s -> V012_CasaOnItsWall.CASA_LOT.equals(s.lot()) ? new Structure(s.name(), s.floors(), s.basements(), s.lot(), corrected) : s)
                .toList();
        mongo.save(new CampusStructuresDocument(id, now.campus(), edited, 9, Instant.now()));
        assertThat(V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now())).isZero();
        assertThat(mongo.findById(id, CampusStructuresDocument.class).version()).isEqualTo(9);

        mongo.save(now);
    }
}
