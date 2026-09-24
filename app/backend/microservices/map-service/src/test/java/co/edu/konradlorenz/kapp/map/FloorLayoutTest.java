package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving a whole floor at once, which is what the floor editor does after someone walks a floor.
 *
 * <p>Each test builds its own building, so the order the tests run in never matters and none of
 * them can disturb the placeholder campus the other test classes lean on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // The placeholder campus is these tests' fixture; SurveyedCampusTest covers the survey.
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class FloorLayoutTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildings;
    @Autowired
    private SpaceRepository spaces;

    private static RequestPostProcessor admin() {
        return jwt().jwt(b -> b.subject("layout-admin").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void save(String building, String body, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(put("/api/map/buildings/{code}/floors/P1/layout", building).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expected);
    }

    private static final String TWO_ROOMS = """
            {
              "version": %d,
              "width": 400, "height": 400,
              "status": "VERIFIED",
              "accessibility": "STEP_FREE",
              "corridors": [{"code": "PAS", "name": "Pasillo", "color": "#5B8DEF",
                             "path": [{"x": 20, "y": 220}, {"x": 380, "y": 220}]}],
              "spaces": [
                {"code": "ASC", "name": "Ascensor", "typeCode": "ELEVATOR", "shape": [{"x": 0, "y": 160}, {"x": 40, "y": 160}, {"x": 40, "y": 200}, {"x": 0, "y": 200}]},
                {"code": "101", "doorCode": "101", "name": "Aula 101", "typeCode": "CLASSROOM",
                 "shape": [{"x": 40, "y": 40}, {"x": 120, "y": 40}, {"x": 120, "y": 80}, {"x": 40, "y": 80}], "accessVia": "ASC"},
                {"code": "L1-DEP", "name": "Dirección de Investigaciones", "typeCode": "OFFICE"}
              ]
            }
            """;

    @Test
    @DisplayName("a layout save writes the floor and every space on it, and bumps the version")
    void savesTheWholeFloor() throws Exception {
        buildings.save(MapFixtures.building("LAY1"));

        save("LAY1", TWO_ROOMS.formatted(0), status().isOk());

        mockMvc.perform(get("/api/map/buildings/LAY1/floors/P1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.corridors[0].code").value("PAS"))
                .andExpect(jsonPath("$.spaces.length()").value(3));
    }

    @Test
    @DisplayName("a save against an old version is refused with 409 and changes nothing")
    void staleVersionIsRefused() throws Exception {
        buildings.save(MapFixtures.building("LAY2"));
        save("LAY2", TWO_ROOMS.formatted(0), status().isOk());

        // Someone else's editor still thinks the floor is at version 0.
        save("LAY2", """
                {"version": 0, "width": 400, "height": 400, "spaces": []}
                """, status().isConflict());

        String buildingId = buildings.findByCode("LAY2").orElseThrow().id();
        assertThat(spaces.findByBuildingIdAndFloorCodeOrderByCodeAsc(buildingId, "P1"))
                .as("the stale save must not have deleted anything").hasSize(3);
    }

    @Test
    @DisplayName("a space left out of the layout is deleted, and a new one is created")
    void missingSpacesAreDeletedAndNewOnesCreated() throws Exception {
        buildings.save(MapFixtures.building("LAY3"));
        save("LAY3", TWO_ROOMS.formatted(0), status().isOk());

        save("LAY3", """
                {
                  "version": 1, "width": 400, "height": 400,
                  "spaces": [
                    {"code": "ASC", "name": "Ascensor", "typeCode": "ELEVATOR", "shape": [{"x": 0, "y": 160}, {"x": 40, "y": 160}, {"x": 40, "y": 200}, {"x": 0, "y": 200}]},
                    {"code": "102", "doorCode": "102", "name": "Aula 102", "typeCode": "CLASSROOM",
                     "shape": [{"x": 280, "y": 280}, {"x": 320, "y": 280}, {"x": 320, "y": 320}, {"x": 280, "y": 320}], "accessVia": "ASC"}
                  ]
                }
                """, status().isOk());

        String buildingId = buildings.findByCode("LAY3").orElseThrow().id();
        assertThat(spaces.findByBuildingIdAndFloorCodeOrderByCodeAsc(buildingId, "P1"))
                .extracting(s -> s.code())
                .containsExactly("102", "ASC");
    }

    @Test
    @DisplayName("a drawing with one room on top of another is refused whole: nothing is written")
    void overlappingDrawingIsRefusedWhole() throws Exception {
        buildings.save(MapFixtures.building("LAY4"));

        save("LAY4", """
                {
                  "version": 0, "width": 400, "height": 400,
                  "spaces": [
                    {"code": "A", "doorCode": "A", "name": "Uno", "typeCode": "OFFICE", "shape": [{"x": 40, "y": 40}, {"x": 80, "y": 40}, {"x": 80, "y": 80}, {"x": 40, "y": 80}]},
                    {"code": "B", "doorCode": "B", "name": "Dos", "typeCode": "OFFICE", "shape": [{"x": 40, "y": 40}, {"x": 80, "y": 40}, {"x": 80, "y": 80}, {"x": 40, "y": 80}]}
                  ]
                }
                """, status().isBadRequest());

        String buildingId = buildings.findByCode("LAY4").orElseThrow().id();
        assertThat(spaces.findByBuildingIdAndFloorCodeOrderByCodeAsc(buildingId, "P1")).isEmpty();
        assertThat(buildings.findByCode("LAY4").orElseThrow().floor("P1").orElseThrow().version())
                .as("the floor keeps its version when the save is refused").isZero();
    }

    @Test
    @DisplayName("rooms that share a wall do not overlap, and a room in L is kept exactly as drawn")
    void sharedWallsAndLShapes() throws Exception {
        buildings.save(MapFixtures.building("LAY7"));

        // 701 is an L: 200 wide along the top, 80 wide down the left. 702 fills the corner the L
        // leaves, sharing two walls with it.
        save("LAY7", """
                {
                  "version": 0, "width": 400, "height": 400,
                  "outline": [{"x": 0, "y": 0}, {"x": 400, "y": 0}, {"x": 400, "y": 400}, {"x": 0, "y": 400}],
                  "spaces": [
                    {"code": "701", "doorCode": "701", "name": "Aula 701", "typeCode": "CLASSROOM",
                     "shape": [{"x": 0, "y": 0}, {"x": 200, "y": 0}, {"x": 200, "y": 80},
                               {"x": 80, "y": 80}, {"x": 80, "y": 200}, {"x": 0, "y": 200}]},
                    {"code": "702", "doorCode": "702", "name": "Aula 702", "typeCode": "CLASSROOM",
                     "shape": [{"x": 80, "y": 80}, {"x": 200, "y": 80}, {"x": 200, "y": 200}, {"x": 80, "y": 200}]}
                  ]
                }
                """, status().isOk());

        mockMvc.perform(get("/api/map/buildings/LAY7/floors/P1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outline.length()").value(4))
                .andExpect(jsonPath("$.spaces[?(@.code == '701')].shape.length()").value(6))
                .andExpect(jsonPath("$.spaces[?(@.code == '701')].shape[3].x").value(80))
                .andExpect(jsonPath("$.spaces[?(@.code == '701')].bounds.width").value(200))
                .andExpect(jsonPath("$.spaces[?(@.code == '701')].bounds.height").value(200));
    }

    @Test
    @DisplayName("a shape whose edges cross each other is refused: it has no inside to draw")
    void selfCrossingShapeIsRefused() throws Exception {
        buildings.save(MapFixtures.building("LAY8"));

        mockMvc.perform(put("/api/map/buildings/LAY8/floors/P1/layout").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "version": 0, "width": 400, "height": 400,
                                  "spaces": [
                                    {"code": "BOW", "name": "Moño", "typeCode": "OFFICE",
                                     "shape": [{"x": 0, "y": 0}, {"x": 100, "y": 0}, {"x": 0, "y": 100}, {"x": 40, "y": 100}]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'spaces[0].shape')].issue")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("crosses itself"))));
    }

    @Test
    @DisplayName("every problem in a drawing is reported at once, each against its space")
    void everyProblemIsReported() throws Exception {
        buildings.save(MapFixtures.building("LAY5"));

        mockMvc.perform(put("/api/map/buildings/LAY5/floors/P1/layout").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "version": 0, "width": 400, "height": 400,
                                  "spaces": [
                                    {"code": "X", "name": "Fuera", "typeCode": "OFFICE", "shape": [{"x": 480, "y": 40}, {"x": 520, "y": 40}, {"x": 520, "y": 80}, {"x": 480, "y": 80}]},
                                    {"code": "Y", "name": "Tipo raro", "typeCode": "NO_EXISTE"},
                                    {"code": "Z", "name": "Por un salón", "typeCode": "OFFICE", "accessVia": "X"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'spaces[0].shape')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'spaces[1].typeCode')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'spaces[2].accessVia')]").exists());
    }

    @Test
    @DisplayName("removing the lift another floor's rooms say to take is refused with 409")
    void removingWhatAnotherFloorUsesIsRefused() throws Exception {
        buildings.save(MapFixtures.building("LAY6", List.of(),
                MapFixtures.floor("P1", 1), MapFixtures.floor("P2", 2)));
        save("LAY6", TWO_ROOMS.formatted(0), status().isOk());
        mockMvc.perform(put("/api/map/buildings/LAY6/floors/P2/layout").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "version": 0, "width": 400, "height": 400,
                                  "spaces": [
                                    {"code": "201", "doorCode": "201", "name": "Aula 201", "typeCode": "CLASSROOM",
                                     "shape": [{"x": 40, "y": 40}, {"x": 80, "y": 40}, {"x": 80, "y": 80}, {"x": 40, "y": 80}], "accessVia": "ASC"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        save("LAY6", """
                {"version": 1, "width": 400, "height": 400, "spaces": []}
                """, status().isConflict());
    }
}
