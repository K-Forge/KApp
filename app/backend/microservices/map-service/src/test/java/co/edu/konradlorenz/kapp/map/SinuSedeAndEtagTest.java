package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.migration.V015_CentralBuildingSede;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Map 3.6: the names SINU gives a building, and reads a client can cache.
 *
 * <p>A sede is how the schedule service finds the building of a class, so one sede on two buildings
 * would put a class in whichever came first: the second building to claim it is refused. And every
 * successful read carries an {@code ETag} that turns into {@code 304} while nothing changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // The placeholder campus is these tests' fixture; the survey has its own tests.
        "kapp.map.survey-seed=false"
})
class SinuSedeAndEtagTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildings;

    @Autowired
    private MongoTemplate mongo;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(b -> b.subject("sede-" + role.toLowerCase()).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static String building(String code, String sedes) {
        return """
                {
                  "code": "%s",
                  "name": "Fixture %s",
                  "campus": "Sede Test",
                  "floors": [{"code": "P1", "level": 1, "name": "Piso 1", "width": 400, "height": 400}]%s
                }
                """.formatted(code, code, sedes == null ? "" : ",\n  \"sinuSedes\": " + sedes);
    }

    @BeforeEach
    void dropFixtures() {
        buildings.findAll().stream()
                .filter(b -> b.code().startsWith("ZS"))
                .forEach(buildings::delete);
    }

    // ------------------------------------------------------------------ sedes

    @Test
    @DisplayName("a building keeps the sedes it was created with, trimmed and once each")
    void createKeepsSedes() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(building("ZS1", "[\" Sede Uno \", \"Sede Uno\", \"Sede Dos\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sinuSedes.length()").value(2))
                .andExpect(jsonPath("$.sinuSedes[0]").value("Sede Uno"))
                .andExpect(jsonPath("$.sinuSedes[1]").value("Sede Dos"));

        mockMvc.perform(get("/api/map/buildings/ZS1").with(as("ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinuSedes[0]").value("Sede Uno"));
    }

    @Test
    @DisplayName("a building created without sedes answers an empty list")
    void createWithoutSedes() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS2", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sinuSedes").isArray())
                .andExpect(jsonPath("$.sinuSedes.length()").value(0));
    }

    @Test
    @DisplayName("PUT without sedes keeps them; with sedes replaces them; with none clears them")
    void updateKeepsReplacesOrClears() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS3", "[\"Sede Tres\"]")))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/map/buildings/ZS3").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS3", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinuSedes[0]").value("Sede Tres"));

        mockMvc.perform(put("/api/map/buildings/ZS3").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS3", "[\"Sede Cuatro\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinuSedes.length()").value(1))
                .andExpect(jsonPath("$.sinuSedes[0]").value("Sede Cuatro"));

        mockMvc.perform(put("/api/map/buildings/ZS3").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS3", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinuSedes.length()").value(0));
    }

    @Test
    @DisplayName("a sede another building has is refused with 409, on create and on update, and nothing changes")
    void sedeOfAnotherBuildingIsAConflict() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS4", "[\"Sede Compartida\"]")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS5", "[\"Sede Compartida\"]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].field").value("sinuSedes"))
                .andExpect(jsonPath("$.details[0].issue").value("Sede Sede Compartida already belongs to building ZS4"));
        assertThat(buildings.findByCode("ZS5")).isEmpty();

        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS5", "[\"Sede Cinco\"]")))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/map/buildings/ZS5").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS5", "[\"Sede Compartida\"]")))
                .andExpect(status().isConflict());
        assertThat(buildings.findByCode("ZS5").orElseThrow().sinuSedes()).containsExactly("Sede Cinco");
    }

    @Test
    @DisplayName("a building may be saved again with its own sedes")
    void ownSedesAreNotAConflict() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS6", "[\"Sede Seis\"]")))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/map/buildings/ZS6").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS6", "[\"Sede Seis\"]")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- change unit

    @Test
    @DisplayName("V015 maps Sede Principal to EC once, and never over a mapping somebody made")
    void centralBuildingSede() {
        buildings.save(MapFixtures.building("EC"));
        try {
            assertThat(V015_CentralBuildingSede.map(mongo)).isTrue();
            assertThat(buildings.findByCode("EC").orElseThrow().sinuSedes()).containsExactly("Sede Principal");
            // Run again: already mapped, nothing changes.
            assertThat(V015_CentralBuildingSede.map(mongo)).isFalse();

            // Somebody moved the sede to another building in the portal: it stays there.
            buildings.findByCode("EC").ifPresent(buildings::delete);
            buildings.save(MapFixtures.building("EC"));
            buildings.save(withSedes(MapFixtures.building("ZS7"), List.of("Sede Principal")));
            assertThat(V015_CentralBuildingSede.map(mongo)).isFalse();
            assertThat(buildings.findByCode("EC").orElseThrow().sinuSedes()).isEmpty();
        } finally {
            buildings.findByCode("EC").ifPresent(buildings::delete);
            buildings.findByCode("ZS7").ifPresent(buildings::delete);
        }
    }

    @Test
    @DisplayName("V015 leaves alone an EC that already has a sede of its own")
    void centralBuildingWithItsOwnSede() {
        buildings.save(withSedes(MapFixtures.building("EC"), List.of("Sede Central")));
        try {
            assertThat(V015_CentralBuildingSede.map(mongo)).isFalse();
            assertThat(buildings.findByCode("EC").orElseThrow().sinuSedes()).containsExactly("Sede Central");
        } finally {
            buildings.findByCode("EC").ifPresent(buildings::delete);
        }
    }

    private static BuildingDocument withSedes(
            BuildingDocument b, List<String> sedes) {
        return new BuildingDocument(b.id(), b.code(), b.name(), b.campus(),
                b.description(), b.aliases(), b.wings(), b.floors(), b.placeholder(), b.createdAt(), b.updatedAt(),
                b.placement(), b.footprint(), b.address(), sedes);
    }

    // -------------------------------------------------------------------- ETag

    @Test
    @DisplayName("a read carries an ETag, and sending it back answers 304 with no body")
    void etagTurnsInto304() throws Exception {
        String etag = mockMvc.perform(get("/api/map/buildings/A").with(as("ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/map/buildings/A").with(as("ROLE_STAFF"))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("the list, a floor and the campuses carry an ETag too")
    void everyReadCarriesAnEtag() throws Exception {
        for (String path : List.of("/api/map/buildings", "/api/map/buildings/A/floors/P3", "/api/map/campuses")) {
            String etag = mockMvc.perform(get(path).with(as("ROLE_GUEST")))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ETAG))
                    .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
            mockMvc.perform(get(path).with(as("ROLE_GUEST")).header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified());
        }
    }

    @Test
    @DisplayName("an edit changes the ETag, so the old one gets the new answer")
    void editChangesTheEtag() throws Exception {
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS8", null)))
                .andExpect(status().isCreated());
        String before = mockMvc.perform(get("/api/map/buildings/ZS8").with(as("ROLE_STUDENT")))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(put("/api/map/buildings/ZS8").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS8", "[\"Sede Ocho\"]")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/map/buildings/ZS8").with(as("ROLE_STUDENT"))
                        .header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinuSedes[0]").value("Sede Ocho"));
    }

    @Test
    @DisplayName("a refused read gets no ETag, and a write carries none")
    void onlySuccessfulReadsAreTagged() throws Exception {
        mockMvc.perform(get("/api/map/buildings/A"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
        mockMvc.perform(post("/api/map/buildings").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(building("ZS9", null)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }
}
