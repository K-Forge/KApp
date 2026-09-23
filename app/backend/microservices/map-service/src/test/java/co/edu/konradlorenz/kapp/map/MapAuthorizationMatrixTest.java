package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorization matrix, checked exhaustively.
 *
 * <p>This service is the only one {@code ROLE_GUEST} can read, which makes it the one
 * service where "a read endpoint quietly stopped allowing guests" produces no failing test
 * for anyone holding a normal account - every other role would still see it work. So every
 * read endpoint gets an explicit assertion for GUEST, STUDENT, PROFESSOR and ADMIN, and
 * every write endpoint gets an explicit assertion that GUEST, STUDENT and PROFESSOR are
 * refused with 403 while ADMIN succeeds. Anonymous (no token at all) is checked everywhere
 * too, and must always be 401, never 403 - a missing token and a wrong role are different
 * failures and the contract distinguishes them.
 *
 * <p>Deliberately not a loop over a list of (role, endpoint) pairs: a loop reports "matrix
 * failed" and leaves you to bisect it by hand. Every case here is its own literal
 * {@code mockMvc.perform(...).andExpect(...)} so a failure names the exact endpoint and role
 * that broke.
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
class MapAuthorizationMatrixTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildings;

    @Autowired
    private SpaceRepository spaces;

    // ---------------------------------------------------------------- roles

    private static RequestPostProcessor asRole(String shortRole) {
        String role = "ROLE_" + shortRole;
        return jwt()
                .jwt(b -> b.subject("matrix-" + shortRole.toLowerCase())
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static RequestPostProcessor guest() {
        return asRole("GUEST");
    }

    private static RequestPostProcessor student() {
        return asRole("STUDENT");
    }

    private static RequestPostProcessor professor() {
        return asRole("PROFESSOR");
    }

    private static RequestPostProcessor admin() {
        return asRole("ADMIN");
    }

    // ---------------------------------------------------------- fixtures

    /** A building the seed migration does not know about, for writes to mutate freely. */
    private BuildingDocument saveBuilding(String code, boolean withSpaceLater) {
        return buildings.save(MapFixtures.building(code));
    }

    private void saveSpace(BuildingDocument building, String code) {
        spaces.save(MapFixtures.space(building, code, "P1", 1, 1));
    }

    private static final String NEW_BUILDING_JSON = """
            {
              "code": "ZG1",
              "name": "Auth Matrix Building",
              "campus": "Sede Test",
              "floors": [
                {"code": "P1", "level": 1, "name": "Piso 1", "gridRows": 10, "gridColumns": 10}
              ]
            }
            """;

    private static String updateBuildingJson(String code) {
        return """
                {
                  "code": "%s",
                  "name": "Renamed fixture",
                  "campus": "Sede Test",
                  "floors": [
                    {"code": "P1", "level": 1, "name": "Piso 1", "gridRows": 10, "gridColumns": 10}
                  ]
                }
                """.formatted(code);
    }

    private static String newSpaceJson(String code) {
        return """
                {
                  "code": "%s",
                  "name": "Auth Matrix Space",
                  "typeCode": "OFFICE",
                  "buildingCode": "A",
                  "floorCode": "P1",
                  "aliases": [],
                  "gridRow": 1,
                  "gridColumn": 1
                }
                """.formatted(code);
    }

    private static String updateSpaceJson(String code) {
        return """
                {
                  "code": "%s",
                  "name": "Renamed fixture space",
                  "typeCode": "OFFICE",
                  "buildingCode": "A",
                  "floorCode": "P1",
                  "aliases": [],
                  "gridRow": 2,
                  "gridColumn": 2
                }
                """.formatted(code);
    }

    // ================================================================
    // READS - GUEST, STUDENT, PROFESSOR and ADMIN must all succeed.
    // Anonymous (no token) must be refused with 401, never 403.
    // ================================================================

    @Test
    @DisplayName("GET /api/map/buildings: guest, student, professor and admin succeed; anonymous is 401")
    void listBuildings_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/buildings").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/map/buildings/{code}: guest, student, professor and admin succeed; anonymous is 401")
    void getBuildingByCode_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/buildings/A").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/map/buildings/{code}/floors/{floorCode}: guest, student, professor and admin succeed; anonymous is 401")
    void getFloor_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/buildings/A/floors/P3").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A/floors/P3").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A/floors/P3").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A/floors/P3").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/A/floors/P3")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/map/spaces/search: guest, student, professor and admin succeed; anonymous is 401")
    void searchSpaces_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/spaces/search").param("q", "aula").with(guest()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/search").param("q", "aula").with(student()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/search").param("q", "aula").with(professor()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/search").param("q", "aula").with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/search").param("q", "aula"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/map/spaces/{code}: guest, student, professor and admin succeed; anonymous is 401")
    void getSpaceByCode_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/spaces/302").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/302").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/302").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/302").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/spaces/302")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/map/campuses: guest, student, professor and admin succeed; anonymous is 401")
    void listCampuses_authorizationMatrix() throws Exception {
        mockMvc.perform(get("/api/map/campuses").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/campuses").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/campuses").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/campuses").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/campuses")).andExpect(status().isUnauthorized());
    }

    // ================================================================
    // WRITES - GUEST, STUDENT and PROFESSOR must all be refused with 403.
    // ADMIN must succeed. Anonymous must be 401, never 403.
    // ================================================================

    @Test
    @DisplayName("POST /api/map/buildings: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void createBuilding_authorizationMatrix() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(guest())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_BUILDING_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/buildings").with(student())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_BUILDING_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/buildings").with(professor())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_BUILDING_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/buildings")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_BUILDING_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/map/buildings").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_BUILDING_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/map/buildings/{code}: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void updateBuilding_authorizationMatrix() throws Exception {
        saveBuilding("ZU1", false);
        String body = updateBuildingJson("ZU1");

        mockMvc.perform(put("/api/map/buildings/ZU1").with(guest())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZU1").with(student())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZU1").with(professor())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZU1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/map/buildings/ZU1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/map/buildings/{code}: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void deleteBuilding_authorizationMatrix() throws Exception {
        saveBuilding("ZD1", false);

        mockMvc.perform(delete("/api/map/buildings/ZD1").with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/buildings/ZD1").with(student())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/buildings/ZD1").with(professor())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/buildings/ZD1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/map/buildings/ZD1").with(admin())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/map/spaces: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void createSpace_authorizationMatrix() throws Exception {
        String body = newSpaceJson("ZS1");

        mockMvc.perform(post("/api/map/spaces").with(guest())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/spaces").with(student())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/spaces").with(professor())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/spaces")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/map/spaces/{code}: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void updateSpace_authorizationMatrix() throws Exception {
        BuildingDocument buildingA = buildings.findByCode("A").orElseThrow();
        saveSpace(buildingA, "ZU2");
        String body = updateSpaceJson("ZU2");

        mockMvc.perform(put("/api/map/spaces/ZU2").with(guest())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/spaces/ZU2").with(student())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/spaces/ZU2").with(professor())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/spaces/ZU2")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/map/spaces/ZU2").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/map/spaces/{code}: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void deleteSpace_authorizationMatrix() throws Exception {
        BuildingDocument buildingA = buildings.findByCode("A").orElseThrow();
        saveSpace(buildingA, "ZD2");

        mockMvc.perform(delete("/api/map/spaces/ZD2").with(guest())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/spaces/ZD2").with(student())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/spaces/ZD2").with(professor())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/spaces/ZD2")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/map/spaces/ZD2").with(admin())).andExpect(status().isNoContent());
    }

    // ================================================================
    // The space type catalogue and the floor layout save.
    // ================================================================

    @Test
    @DisplayName("GET /api/map/space-types: guest, student, professor and admin succeed; anonymous is 401")
    void listSpaceTypes_allReadersSucceed() throws Exception {
        mockMvc.perform(get("/api/map/space-types").with(guest())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/space-types").with(student())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/space-types").with(professor())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/space-types").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/map/space-types")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST, PUT and DELETE /api/map/space-types: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void spaceTypeWrites_authorizationMatrix() throws Exception {
        String body = """
                {"code": "ZZ_MATRIX", "name": "Tipo de prueba", "category": "OTHER"}
                """;
        mockMvc.perform(post("/api/map/space-types").with(guest())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/space-types").with(student())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/space-types").with(professor())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/map/space-types")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/map/space-types").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mockMvc.perform(put("/api/map/space-types/ZZ_MATRIX").with(student())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/space-types/ZZ_MATRIX").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/map/space-types/ZZ_MATRIX").with(professor()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/map/space-types/ZZ_MATRIX")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/map/space-types/ZZ_MATRIX").with(admin())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /api/map/buildings/{code}/floors/{floorCode}/layout: guest, student and professor are refused; admin succeeds; anonymous is 401")
    void saveLayout_authorizationMatrix() throws Exception {
        buildings.save(MapFixtures.building("ZL1"));
        String body = """
                {"version": 0, "gridRows": 10, "gridColumns": 10, "spaces": []}
                """;
        mockMvc.perform(put("/api/map/buildings/ZL1/floors/P1/layout").with(guest())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZL1/floors/P1/layout").with(student())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZL1/floors/P1/layout").with(professor())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/map/buildings/ZL1/floors/P1/layout")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/map/buildings/ZL1/floors/P1/layout").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
}
