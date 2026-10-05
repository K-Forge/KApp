package co.edu.konradlorenz.kapp.map;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What else stands on a campus's blocks: read by anybody who reads the map, saved whole by an
 * admin, and a save made on an older list refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class StructuresTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(b -> b.subject("structures-" + role).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static String list(long version, String ring) {
        return """
                {"version": %d, "structures": [
                  {"name": "Casa vecina", "floors": 2, "basements": 0, "lot": "008213024001", "ring": %s}]}
                """.formatted(version, ring);
    }

    private static final String RING = "[[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0613, 4.6485]]";

    @Test
    @DisplayName("a campus nobody has saved structures for has none, at version 0, for every reader of the map")
    void noneYet() throws Exception {
        for (String role : List.of("ROLE_GUEST", "ROLE_STUDENT", "ROLE_PROFESSOR", "ROLE_ADMIN")) {
            mockMvc.perform(get("/api/map/campuses/{campus}/structures", "Sede Vacía").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.structures.length()").value(0))
                    .andExpect(jsonPath("$.version").value(0));
        }
        mockMvc.perform(get("/api/map/campuses/{campus}/structures", "Sede Vacía"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only an admin saves them; without a token it is 401, with another role 403")
    void onlyAdminsSave() throws Exception {
        for (String role : List.of("ROLE_GUEST", "ROLE_STUDENT", "ROLE_PROFESSOR")) {
            mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Roles").with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(list(0, RING)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Roles")
                        .contentType(MediaType.APPLICATION_JSON).content(list(0, RING)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a save carries the version it read, and one made on an older list changes nothing")
    void staleSaveIsRefused() throws Exception {
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Versiones").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(list(0, RING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.structures[0].name").value("Casa vecina"));
        // A second editor, who opened the empty list too.
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Versiones").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"structures\": []}"))
                .andExpect(status().isConflict());
        // Case and accents do not matter, as for the ground.
        mockMvc.perform(get("/api/map/campuses/{campus}/structures", "SEDE VERSIONES").with(as("ROLE_GUEST")))
                .andExpect(jsonPath("$.campus").value("Sede Versiones"))
                .andExpect(jsonPath("$.structures[0].floors").value(2))
                .andExpect(jsonPath("$.structures[0].ring[0][0]").value(-74.0613));
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Versiones").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 1, \"structures\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.structures.length()").value(0));
    }

    @Test
    @DisplayName("an outline that does not close, or a structure with no name, is refused")
    void impossibleStructuresAreRefused() throws Exception {
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Errores").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(list(0, "[[-74.0613, 4.6485], [-74.0612, 4.6486], [-74.0611, 4.6485], [-74.0610, 4.6484]]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/map/campuses/{campus}/structures", "Sede Errores").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(list(0, RING).replace("Casa vecina", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/map/campuses/{campus}/structures", "Sede Errores").with(as("ROLE_ADMIN")))
                .andExpect(jsonPath("$.version").value(0));
    }
}
