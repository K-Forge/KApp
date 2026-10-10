package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.Wing;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The domain rules that are easy to get backwards: a cascade instead of a refusal, a
 * two-call round trip where the contract promises one, an ambiguous code silently resolved
 * to the wrong building. Each of these was called out explicitly in the brief because
 * getting it wrong is data loss or a wrong answer, not a crash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // The placeholder campus is these tests' fixture; SurveyedCampusTest covers the survey.
        "kapp.map.survey-seed=false"
})
class MapBusinessRulesTest {

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
        return jwt()
                .jwt(b -> b.subject("rules-admin").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor guest() {
        return jwt()
                .jwt(b -> b.subject("rules-guest").claim("roles", List.of("ROLE_GUEST")))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
    }

    @Test
    @DisplayName("GET /api/map/spaces/{code} returns the space, its floor and its building in one call")
    void spaceDetailComposesSpaceFloorAndBuilding() throws Exception {
        mockMvc.perform(get("/api/map/spaces/708").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("708"))
                .andExpect(jsonPath("$.buildingCode").value("A"))
                .andExpect(jsonPath("$.floor.code").value("P7"))
                .andExpect(jsonPath("$.floorCode").value("P7"))
                .andExpect(jsonPath("$.floor.width").value(640))
                .andExpect(jsonPath("$.floor.height").value(440))
                .andExpect(jsonPath("$.building.code").value("A"))
                .andExpect(jsonPath("$.building.name").value("Bloque A"));
    }

    @Test
    @DisplayName("deleting a building that still has spaces is refused with 409 and nothing is deleted")
    void deletingOccupiedBuildingIsConflict() throws Exception {
        mockMvc.perform(delete("/api/map/buildings/A").with(admin()))
                .andExpect(status().isConflict());

        assertThat(buildings.existsByCode("A"))
                .as("the building must survive the refused delete")
                .isTrue();
        assertThat(spaces.findByBuildingId(buildings.findByCode("A").orElseThrow().id()))
                .as("none of its spaces were cascaded away")
                .isNotEmpty();
    }

    @Test
    @DisplayName("dropping a floor that still has spaces on it is refused with 409")
    void droppingOccupiedFloorIsConflict() throws Exception {
        BuildingDocument occupied = buildings.save(MapFixtures.building("OCC", List.of(),
                MapFixtures.floor("P1", 1), MapFixtures.floor("P2", 2)));
        spaces.save(MapFixtures.space(occupied, "OCCSP", "P2", 40, 40));

        String bodyDroppingFloor2 = """
                {
                  "code": "OCC",
                  "name": "Occupied Floor Fixture",
                  "campus": "Sede Test",
                  "floors": [
                    {"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}
                  ]
                }
                """;

        mockMvc.perform(put("/api/map/buildings/OCC").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDroppingFloor2))
                .andExpect(status().isConflict());

        assertThat(buildings.findByCode("OCC").orElseThrow().floors())
                .as("the floor was not actually dropped")
                .hasSize(2);
    }

    @Test
    @DisplayName("a room code shared by two buildings is refused with 409 listing the candidate buildings")
    void ambiguousRoomCodeListsCandidates() throws Exception {
        BuildingDocument amb1 = buildings.save(MapFixtures.building("AMB1"));
        BuildingDocument amb2 = buildings.save(MapFixtures.building("AMB2"));
        spaces.save(MapFixtures.space(amb1, "AMB", "P1", 40, 40));
        spaces.save(MapFixtures.space(amb2, "AMB", "P1", 40, 40));

        mockMvc.perform(get("/api/map/spaces/AMB").with(guest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].field").value("buildingCode"))
                .andExpect(jsonPath("$.details[0].issue").value("Candidate building: AMB1"))
                .andExpect(jsonPath("$.details[1].issue").value("Candidate building: AMB2"));

        // Disambiguated, the same code resolves cleanly.
        mockMvc.perform(get("/api/map/spaces/AMB").param("buildingCode", "AMB2").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.building.code").value("AMB2"));
    }

    // ── The basement, and listing without searching ────────────────────────────────

    @Test
    @DisplayName("a basement floor can be read back, not only written")
    void basementFloorIsReadable() throws Exception {
        buildings.save(MapFixtures.building("SOT", List.of(), MapFixtures.floor("S1", -1)));

        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "SOT-01",
                                  "doorCode": "SOT-01",
                                  "name": "Deposito",
                                  "typeCode": "STORAGE",
                                  "buildingCode": "SOT",
                                  "floorCode": "S1",
                                  "shape": [{"x": 0, "y": 0}, {"x": 40, "y": 0}, {"x": 40, "y": 40}, {"x": 0, "y": 40}]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/map/buildings/{code}/floors/{floor}", "SOT", "S1").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S1"))
                .andExpect(jsonPath("$.spaces[0].code").value("SOT-01"));
    }

    @Test
    @DisplayName("the search lists a whole building when given no term")
    void searchWithoutATermListsTheBuilding() throws Exception {
        mockMvc.perform(get("/api/map/spaces/search").param("buildingCode", "A").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("listing without a term still honours the other filters")
    void listingHonoursTheFilters() throws Exception {
        String body = mockMvc.perform(get("/api/map/spaces/search")
                        .param("type", "ELEVATOR").with(guest()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("ELEVATOR");
        assertThat(body).doesNotContain("CLASSROOM");
    }

    @Test
    @DisplayName("a term made only of text-query punctuation still answers an empty page")
    void punctuationOnlyTermIsEmptyNotEverything() throws Exception {
        // The distinction matters now that a missing term means "list everything": a term of
        // '"""' sanitizes to nothing, and that must stay an empty page rather than becoming an
        // unfiltered listing of the campus.
        mockMvc.perform(get("/api/map/spaces/search").param("q", "\"\"\"").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── The schematic model's own rules ────────────────────────────────────────────

    @Test
    @DisplayName("a space that would not fit on its floor is refused with 400")
    void spaceOutsideTheFloorIsRejected() throws Exception {
        // Floor 3 of Bloque A is 640 x 440 units, so x = 800 is off the drawing.
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OUT-1",
                                  "name": "Fuera del piso",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P3",
                                  "shape": [{"x": 800, "y": 80}, {"x": 840, "y": 80}, {"x": 840, "y": 120}, {"x": 800, "y": 120}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("shape"));
    }

    @Test
    @DisplayName("a space that runs off the edge is refused, not silently clipped")
    void shapeRunningOffTheEdgeIsRejected() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OUT-2",
                                  "name": "Se sale por el borde",
                                  "typeCode": "AUDITORIUM",
                                  "buildingCode": "A",
                                  "floorCode": "P3",
                                  "shape": [{"x": 560, "y": 80}, {"x": 800, "y": 80}, {"x": 800, "y": 120}, {"x": 560, "y": 120}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("two spaces cannot cover the same area: the second would be hidden, not obviously wrong")
    void overlappingSpacesAreRefused() throws Exception {
        // Room 301 covers x 160-240, y 200-240 of floor 3.
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OVER-1",
                                  "name": "Encima de 301",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P3",
                                  "shape": [{"x": 200, "y": 200}, {"x": 240, "y": 200}, {"x": 240, "y": 240}, {"x": 200, "y": 240}]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].issue").value(org.hamcrest.Matchers.containsString("'301'")));
    }

    @Test
    @DisplayName("the same place on a different floor is fine")
    void samePlaceOnAnotherFloorIsAllowed() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OK-1",
                                  "name": "Mismo lugar, otro piso",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 160, "y": 200}, {"x": 240, "y": 200}, {"x": 240, "y": 240}, {"x": 160, "y": 240}]
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a space in the basement is accepted: S1 is a floor like any other")
    void basementSpacesAreAccepted() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "S-99",
                                  "name": "Cuarto de máquinas",
                                  "typeCode": "OTHER",
                                  "buildingCode": "A",
                                  "floorCode": "S1",
                                  "shape": [{"x": 0, "y": 0}, {"x": 40, "y": 0}, {"x": 40, "y": 40}, {"x": 0, "y": 40}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floorCode").value("S1"));
    }

    @Test
    @DisplayName("the base code drops the suffix the wing declares, so 205-N is found as 205")
    void baseCodeDropsTheWingsDoorSuffix() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "205-N",
                                  "doorCode": "205-N",
                                  "wing": "N",
                                  "name": "Aula 205 Norte",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 320, "y": 40}, {"x": 360, "y": 40}, {"x": 360, "y": 80}, {"x": 320, "y": 80}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wing").value("N"))
                .andExpect(jsonPath("$.baseCode").value("205"));
    }

    @Test
    @DisplayName("a wing the building does not declare is refused: Bienestar's are not north and south")
    void undeclaredWingIsRefused() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "206",
                                  "doorCode": "206",
                                  "wing": "OCC",
                                  "name": "Aula 206",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 320, "y": 320}, {"x": 360, "y": 320}, {"x": 360, "y": 360}, {"x": 320, "y": 360}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("wing"));
    }

    @Test
    @DisplayName("a space with nothing on its door has no door code, and its code never becomes one")
    void spaceWithoutDoorCode() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "A-P2-DEP",
                                  "name": "Dirección de Revistas Científicas",
                                  "typeCode": "OFFICE",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 480, "y": 360}, {"x": 520, "y": 360}, {"x": 520, "y": 400}, {"x": 480, "y": 400}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("A-P2-DEP"))
                .andExpect(jsonPath("$.doorCode").doesNotExist())
                .andExpect(jsonPath("$.baseCode").doesNotExist());

        // The search matches door codes, not internal ones: typing the generated code finds nothing.
        mockMvc.perform(get("/api/map/spaces/search").param("q", "A-P2-DEP").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/map/spaces/search").param("q", "revistas").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("A-P2-DEP"));
    }

    @Test
    @DisplayName("a space known from its plaque but not yet drawn is stored unplaced, and the search still finds it")
    void unplacedSpaceIsStoredAndFound() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "A-P2-SIN",
                                  "name": "Sala de Formación Científica",
                                  "typeCode": "MEETING_ROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shape").doesNotExist());

        mockMvc.perform(get("/api/map/spaces/search").param("q", "formacion cientifica").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("A-P2-SIN"))
                .andExpect(jsonPath("$.content[0].category").value("OFFICE"));
    }

    @Test
    @DisplayName("a shape of fewer than three corners is refused: it has no area to draw")
    void shapeWithoutAreaIsRefused() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "LINE-1",
                                  "name": "Solo una línea",
                                  "typeCode": "OFFICE",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 40, "y": 40}, {"x": 80, "y": 40}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("shape"));
    }

    @Test
    @DisplayName("a space inherits its floor's accessibility unless it states its own")
    void accessibilityIsInheritedUnlessStated() throws Exception {
        // Bloque B's mezzanine is only reachable by stairs, and its lab says nothing of its own.
        mockMvc.perform(get("/api/map/buildings/B/floors/MEZZ").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessibility").value("STAIRS_ONLY"))
                .andExpect(jsonPath("$.spaces[0].accessibility").doesNotExist())
                .andExpect(jsonPath("$.spaces[0].effectiveAccessibility").value("STAIRS_ONLY"));
    }

    @Test
    @DisplayName("the lift other rooms say to take cannot be deleted from under them")
    void deletingAnAccessViaTargetIsRefused() throws Exception {
        mockMvc.perform(delete("/api/map/spaces/B-ASC").param("buildingCode", "B").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a building is found by any of its aliases, ignoring accents")
    void buildingsAreFoundByAlias() throws Exception {
        mockMvc.perform(get("/api/map/buildings").param("q", "psicologia").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("B"));
    }

    @Test
    @DisplayName("floors come back in vertical order, the mezzanine between the floors it sits between")
    void mezzanineSortsBetweenItsFloors() throws Exception {
        mockMvc.perform(get("/api/map/buildings/B").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floors[0].code").value("P1"))
                .andExpect(jsonPath("$.floors[1].code").value("MEZZ"))
                .andExpect(jsonPath("$.floors[2].code").value("P2"));
    }

    @Test
    @DisplayName("dropping a wing that still has spaces in it is refused with 409")
    void droppingOccupiedWingIsConflict() throws Exception {
        BuildingDocument winged = buildings.save(MapFixtures.building("WNG",
                List.of(new Wing("E", "Ala oriental", null, null)), MapFixtures.floor("P1", 1)));
        var space = MapFixtures.space(winged, "WNG-1", "P1", 40, 40);
        spaces.save(new co.edu.konradlorenz.kapp.map.domain.SpaceDocument(space.id(), space.code(),
                space.doorCode(), space.baseCode(), "E", space.name(), space.typeCode(),
                space.buildingId(), space.buildingCode(), space.campus(), space.floorCode(),
                space.floorLevel(), space.aliases(), space.shape(), space.doors(), null, null, null, null, false,
                space.createdAt(), space.updatedAt()));

        mockMvc.perform(put("/api/map/buildings/WNG").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "WNG",
                                  "name": "Sin alas",
                                  "campus": "Sede Test",
                                  "floors": [
                                    {"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("accessVia must name a real circulation element, or the app tells a visitor to use a lift that is not there")
    void accessViaMustResolve() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "AV-1",
                                  "name": "Se llega por un ascensor inventado",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 0, "y": 0}, {"x": 40, "y": 0}, {"x": 40, "y": 40}, {"x": 0, "y": 40}],
                                  "accessVia": "ASC-QUE-NO-EXISTE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("accessVia"));
    }

    @Test
    @DisplayName("accessVia may not point at an ordinary room: nobody travels through a classroom")
    void accessViaMustBeCirculation() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "AV-2",
                                  "name": "Se llega por otro salón",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 80, "y": 0}, {"x": 120, "y": 0}, {"x": 120, "y": 40}, {"x": 80, "y": 40}],
                                  "accessVia": "302"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("accessVia"));
    }

    @Test
    @DisplayName("a real lift is accepted, including from another floor of the same building")
    void accessViaAcceptsARealLift() throws Exception {
        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "AV-3",
                                  "name": "Se llega por el ascensor central",
                                  "typeCode": "CLASSROOM",
                                  "buildingCode": "A",
                                  "floorCode": "P2",
                                  "shape": [{"x": 160, "y": 0}, {"x": 200, "y": 0}, {"x": 200, "y": 40}, {"x": 160, "y": 40}],
                                  "accessVia": "ASC-CENTRAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessVia").value("ASC-CENTRAL"));
    }
}
