package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A building laid on the ground, and the ground a campus stands on. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GroundTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildings;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(b -> b.subject("ground-" + role).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static final String LAID = """
            {"code": "GRD1", "name": "Laid", "campus": "Sede Test",
             "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}],
             "placement": {"origin": {"lat": 4.6485322, "lon": -74.0611553}, "bearing": 127.8, "metresPerUnit": 0.02951}}
            """;

    @Test
    @DisplayName("a building is laid on the ground, and a form that leaves the placement out keeps it")
    void placementIsKept() throws Exception {
        buildings.save(MapFixtures.building("GRD1"));

        mockMvc.perform(put("/api/map/buildings/GRD1").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(LAID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placement.bearing").value(127.8));
        // Renaming it, from a form that does not show the placement.
        mockMvc.perform(put("/api/map/buildings/GRD1").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code": "GRD1", "name": "Renamed", "campus": "Sede Test",
                                 "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/map/buildings/GRD1").with(as("ROLE_GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placement.origin.lat").value(4.6485322))
                .andExpect(jsonPath("$.placement.metresPerUnit").value(0.02951));
    }

    @Test
    @DisplayName("a building keeps its footprint, and a form that leaves it out keeps it too")
    void footprintIsKept() throws Exception {
        buildings.save(MapFixtures.building("GRD3", List.of(new co.edu.konradlorenz.kapp.map.domain.Wing("N", "Ala norte", null, null)),
                MapFixtures.floor("P1", 1)));
        String ring = "[[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]";
        mockMvc.perform(put("/api/map/buildings/GRD3").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code": "GRD3", "name": "Parts", "campus": "Sede Test",
                                 "wings": [{"code": "N", "name": "Ala norte"}],
                                 "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}],
                                 "footprint": [{"lot": "008213024019", "floors": 5, "basements": 0, "wing": "N", "ring": %s}]}
                                """.formatted(ring)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.footprint[0].wing").value("N"));
        mockMvc.perform(put("/api/map/buildings/GRD3").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code": "GRD3", "name": "Renamed", "campus": "Sede Test",
                                 "wings": [{"code": "N", "name": "Ala norte"}],
                                 "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/GRD3").with(as("ROLE_GUEST")))
                .andExpect(jsonPath("$.footprint[0].floors").value(5))
                .andExpect(jsonPath("$.footprint[0].ring[0][0]").value(-74.0613));
    }

    @Test
    @DisplayName("a part the upper floors carry out over the street keeps its lowest floor; one on the street keeps none")
    void overhangKeepsItsLowestFloor() throws Exception {
        buildings.save(MapFixtures.building("GRD5"));
        String ring = "[[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]";
        mockMvc.perform(put("/api/map/buildings/GRD5").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code": "GRD5", "name": "Portico", "campus": "Sede Test",
                                 "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}],
                                 "footprint": [{"floors": 5, "lowestFloor": 2, "basements": 0, "ring": %s},
                                               {"floors": 5, "lowestFloor": 1, "basements": 0, "ring": %s}]}
                                """.formatted(ring, ring)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/map/buildings/GRD5").with(as("ROLE_GUEST")))
                .andExpect(jsonPath("$.footprint[0].lowestFloor").value(2))
                .andExpect(jsonPath("$.footprint[1].lowestFloor").doesNotExist());
    }

    @Test
    @DisplayName("a part in a wing the building does not have, an outline that does not close, or one starting above its top floor, is refused")
    void impossibleFootprintIsRefused() throws Exception {
        buildings.save(MapFixtures.building("GRD4"));
        for (String part : List.of(
                "{\"floors\": 3, \"basements\": 0, \"wing\": \"X\", \"ring\": [[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]}",
                "{\"floors\": 3, \"basements\": 0, \"ring\": [[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0610, 4.6484]]}",
                "{\"floors\": 3, \"lowestFloor\": 4, \"basements\": 0, \"ring\": [[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]}",
                "{\"floors\": 3, \"lowestFloor\": 0, \"basements\": 0, \"ring\": [[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]}")) {
            mockMvc.perform(put("/api/map/buildings/GRD4").with(as("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"code": "GRD4", "name": "Wrong", "campus": "Sede Test",
                                     "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}],
                                     "footprint": [%s]}
                                    """.formatted(part)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("a bearing of a full turn or a drawing without a scale is refused")
    void impossiblePlacementIsRefused() throws Exception {
        buildings.save(MapFixtures.building("GRD2"));
        for (String wrong : List.of("\"bearing\": 360, \"metresPerUnit\": 0.03", "\"bearing\": 90, \"metresPerUnit\": 0")) {
            mockMvc.perform(put("/api/map/buildings/GRD2").with(as("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"code": "GRD2", "name": "Wrong", "campus": "Sede Test",
                                     "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}],
                                     "placement": {"origin": {"lat": 4.6, "lon": -74.0}, %s}}
                                    """.formatted(wrong)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("the ground around the main campus is served to anybody who can read the map, credited to its source")
    void groundIsServed() throws Exception {
        // Case and accents do not matter, as in the building search.
        mockMvc.perform(get("/api/map/campuses/{campus}/ground", "SEDE PRINCIPAL").with(as("ROLE_GUEST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campus").value("Sede Principal"))
                .andExpect(jsonPath("$.source").value(containsString("IDECA")))
                .andExpect(jsonPath("$.blocks.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.sidewalks.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.roadways.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.streets[?(@.name == 'Carrera 9 Bis')]").exists())
                // The lots of the blocks the buildings stand on: the Edificio Central's among them.
                .andExpect(jsonPath("$.lots[?(@.code == '008213024019')]").exists())
                // GeoJSON order: longitude first. Bogota is west of Greenwich and just north of the equator.
                .andExpect(jsonPath("$.blocks[0][0][0]").value(org.hamcrest.Matchers.lessThan(-74.0)))
                .andExpect(jsonPath("$.blocks[0][0][1]").value(greaterThan(4.6)));
    }

    @Test
    @DisplayName("a campus with no ground is 404")
    void unknownCampus() throws Exception {
        mockMvc.perform(get("/api/map/campuses/{campus}/ground", "Sede Nowhere").with(as("ROLE_STUDENT")))
                .andExpect(status().isNotFound());
    }
}
