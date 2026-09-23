package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.web.dto.FloorLayoutRequest;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
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
 * The surveyed campus, loaded the way a real database gets it: every change unit in order, with
 * {@link V005_SurveyedCampus} switched on.
 *
 * <p>Nothing here names a room. The snapshot is rewritten every time the campus is exported
 * from the portal, so what is asserted is what must hold for any snapshot: that it loads whole,
 * that the placeholders are gone, that every floor would be accepted by the floor editor, and
 * that a building somebody already made is never overwritten.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SurveyedCampusTest {

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
    @DisplayName("the placeholder campus is gone: no placeholder building, no placeholder space")
    void placeholdersAreGone() {
        assertThat(mongo.count(query(where("placeholder").is(true)), BuildingDocument.class)).isZero();
        assertThat(mongo.count(query(where("placeholder").is(true)), SpaceDocument.class)).isZero();
    }

    @Test
    @DisplayName("every surveyed building is stored whole: its floors, and each floor's spaces where the snapshot puts them")
    void everyBuildingIsLoadedWhole() {
        for (SurveySnapshot.Building surveyed : SNAPSHOT) {
            BuildingDocument stored = mongo.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
            assertThat(stored).as(surveyed.code()).isNotNull();
            assertThat(stored.placeholder()).isFalse();
            assertThat(stored.aliases()).containsExactlyElementsOf(surveyed.aliases());
            assertThat(stored.floors()).extracting(Floor::code)
                    .containsExactlyInAnyOrderElementsOf(surveyed.floors().stream().map(SurveySnapshot.SnapshotFloor::code).toList());

            for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
                List<SpaceDocument> spaces = mongo.find(query(where("buildingId").is(stored.id())
                        .and("floorCode").is(floor.code())), SpaceDocument.class);
                assertThat(spaces).as(surveyed.code() + " " + floor.code())
                        .extracting(SpaceDocument::code)
                        .containsExactlyInAnyOrderElementsOf(floor.spaces().stream().map(LayoutSpaceDto::code).toList());
                for (LayoutSpaceDto space : floor.spaces()) {
                    SpaceDocument match = spaces.stream().filter(s -> s.code().equals(space.code())).findFirst().orElseThrow();
                    assertThat(match.gridRow()).as(space.code()).isEqualTo(space.gridRow());
                    assertThat(match.gridColumn()).as(space.code()).isEqualTo(space.gridColumn());
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
                        floor.gridRows(), floor.gridColumns(), floor.status(), floor.accessibility(),
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
    @DisplayName("a building somebody already made is left alone, and so is a placeholder that holds real spaces")
    void somebodysWorkIsKept() {
        MongoTemplate fresh = new MongoTemplate(client, "survey_rerun_" + UUID.randomUUID().toString().substring(0, 8));
        Instant now = Instant.now();
        SurveySnapshot.Building first = SNAPSHOT.getFirst();
        fresh.insert(building(first.code(), "Hecho a mano", false, now));
        BuildingDocument inUse = fresh.insert(building("A", "Bloque A", true, now));
        BuildingDocument empty = fresh.insert(building("B", "Bloque B", true, now));
        fresh.insert(space(inUse, "301", false, now));
        fresh.insert(space(empty, "101", true, now));

        V005_SurveyedCampus.Result result = V005_SurveyedCampus.apply(fresh, SNAPSHOT, now);

        assertThat(result.kept()).containsExactly(first.code());
        assertThat(fresh.findOne(query(where("code").is(first.code())), BuildingDocument.class).name())
                .isEqualTo("Hecho a mano");
        assertThat(result.placeholdersInUse()).containsExactly("A");
        assertThat(fresh.exists(query(where("code").is("A")), BuildingDocument.class)).isTrue();
        assertThat(fresh.exists(query(where("code").is("B")), BuildingDocument.class)).isFalse();
        assertThat(result.placeholderSpacesRemoved()).isEqualTo(1);
        assertThat(result.buildingsAdded()).isEqualTo(SNAPSHOT.size() - 1);
    }

    @Test
    @DisplayName("the rollback takes back only the surveyed buildings nobody has worked on since")
    void rollbackKeepsWorkedOnBuildings() {
        MongoTemplate fresh = new MongoTemplate(client, "survey_rollback_" + UUID.randomUUID().toString().substring(0, 8));
        V005_SurveyedCampus.apply(fresh, SNAPSHOT, Instant.now());
        SurveySnapshot.Building worked = SNAPSHOT.stream()
                .filter(b -> b.floors().stream().anyMatch(f -> !f.spaces().isEmpty())).findFirst().orElseThrow();
        BuildingDocument stored = fresh.findOne(query(where("code").is(worked.code())), BuildingDocument.class);
        SpaceDocument edited = fresh.findOne(query(where("buildingId").is(stored.id())), SpaceDocument.class);
        fresh.save(new SpaceDocument(edited.id(), edited.code(), edited.doorCode(), edited.baseCode(), edited.wing(),
                edited.name(), edited.typeCode(), edited.buildingId(), edited.buildingCode(), edited.campus(),
                edited.floorCode(), edited.floorLevel(), edited.aliases(), 0, 0, 1, 1, edited.accessVia(),
                edited.accessibility(), edited.note(), edited.capacity(), false, edited.createdAt(),
                edited.createdAt().plusSeconds(60)));

        new V005_SurveyedCampus().rollback(fresh);

        assertThat(fresh.findAll(BuildingDocument.class)).extracting(BuildingDocument::code)
                .containsExactly(worked.code());
    }

    private static BuildingDocument building(String code, String name, boolean placeholder, Instant now) {
        return new BuildingDocument(UUID.randomUUID().toString(), code, name, "Sede Principal", null,
                List.of(), List.of(),
                List.of(new Floor("P1", 1, "Piso 1", FloorStatus.DRAFT, Accessibility.UNKNOWN, null, 6, 10, List.of(), 0)),
                placeholder, now, now);
    }

    private static SpaceDocument space(BuildingDocument building, String code, boolean placeholder, Instant now) {
        return new SpaceDocument(UUID.randomUUID().toString(), code, code, code, null, "Aula " + code,
                "CLASSROOM", building.id(), building.code(), building.campus(), "P1", 1, List.of(),
                null, null, 1, 1, null, null, null, null, placeholder, now, now);
    }
}
