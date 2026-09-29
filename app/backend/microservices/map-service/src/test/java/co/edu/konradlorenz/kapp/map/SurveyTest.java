package co.edu.konradlorenz.kapp.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The distances taken round a campus's blocks: read by anybody who reads the map, saved whole by
 * an admin, a save made on an older survey refused, and each distance keeping the time it was
 * taken until it changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SurveyTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(b -> b.subject("survey-" + role).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static String survey(long version, String measures) {
        return "{\"version\": %d, \"measures\": [%s]}".formatted(version, measures);
    }

    private static final String SETBACK = """
            {"id": "bis-05", "text": "4,80 + 3,25", "metres": 8.05, "note": "Columns in front of the wall"}""";
    private static final String EXTRA = """
            {"id": "extra-1", "label": "Gate on Cra 9A", "text": "3,10", "metres": 3.1}""";

    @Test
    @DisplayName("a campus nobody has surveyed has no distances, at version 0, for every reader of the map")
    void noneYet() throws Exception {
        for (String role : List.of("ROLE_GUEST", "ROLE_STUDENT", "ROLE_PROFESSOR", "ROLE_ADMIN")) {
            mockMvc.perform(get("/api/map/campuses/{campus}/survey", "Sede Vacía").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measures.length()").value(0))
                    .andExpect(jsonPath("$.version").value(0));
        }
        mockMvc.perform(get("/api/map/campuses/{campus}/survey", "Sede Vacía"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only an admin saves it; without a token it is 401, with another role 403")
    void onlyAdminsSave() throws Exception {
        for (String role : List.of("ROLE_GUEST", "ROLE_STUDENT", "ROLE_PROFESSOR")) {
            mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Roles").with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(survey(0, SETBACK)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Roles")
                        .contentType(MediaType.APPLICATION_JSON).content(survey(0, SETBACK)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a save carries the version it read; one made on an older survey changes nothing")
    void staleSaveIsRefused() throws Exception {
        mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Versiones").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(survey(0, SETBACK + "," + EXTRA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.measures[0].text").value("4,80 + 3,25"))
                .andExpect(jsonPath("$.measures[0].metres").value(8.05))
                .andExpect(jsonPath("$.measures[1].label").value("Gate on Cra 9A"));
        // The phone saved while the laptop still had the empty survey open.
        mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Versiones").with(as("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(survey(0, "")))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/map/campuses/{campus}/survey", "SEDE VERSIONES").with(as("ROLE_GUEST")))
                .andExpect(jsonPath("$.campus").value("Sede Versiones"))
                .andExpect(jsonPath("$.measures.length()").value(2))
                .andExpect(jsonPath("$.measures[0].note").value("Columns in front of the wall"));
    }

    @Test
    @DisplayName("a distance keeps the time it was taken until it changes")
    void distancesKeepWhenTheyWereTaken() throws Exception {
        JsonNode first = json.readTree(mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Tiempos")
                        .with(as("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(survey(0, SETBACK + "," + EXTRA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String setbackTaken = first.at("/measures/0/updatedAt").asText();
        String extraTaken = first.at("/measures/1/updatedAt").asText();
        Thread.sleep(5);

        JsonNode second = json.readTree(mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Tiempos")
                        .with(as("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(survey(1, SETBACK + "," + EXTRA.replace("3,10", "3,15").replace("3.1", "3.15"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.at("/measures/0/updatedAt").asText()).isEqualTo(setbackTaken);
        assertThat(second.at("/measures/1/updatedAt").asText()).isNotEqualTo(extraTaken);
        assertThat(second.at("/measures/1/metres").asDouble()).isEqualTo(3.15);
    }

    @Test
    @DisplayName("two distances with one id, a bad id, or a negative distance are refused")
    void impossibleSurveysAreRefused() throws Exception {
        for (String measures : List.of(SETBACK + "," + SETBACK,
                SETBACK.replace("bis-05", "Bis 05"),
                SETBACK.replace("8.05", "-1"))) {
            mockMvc.perform(put("/api/map/campuses/{campus}/survey", "Sede Errores").with(as("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(survey(0, measures)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/map/campuses/{campus}/survey", "Sede Errores").with(as("ROLE_ADMIN")))
                .andExpect(jsonPath("$.version").value(0));
    }
}
