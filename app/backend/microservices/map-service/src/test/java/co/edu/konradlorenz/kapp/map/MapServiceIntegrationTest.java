package co.edu.konradlorenz.kapp.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Phase 0 exit test. It proves the whole skeleton actually holds together:
 * MongoDB wiring, Mongock migrations, the shared security auto-configuration from
 * {@code common}, role mapping from the {@code roles} claim, and the shared error
 * envelope.
 *
 * <p>Before this, the repository had zero tests across every service and CI passed
 * vacuously - {@code mvn verify} compiled and reported success with nothing to run.
 *
 * <p>Testcontainers' {@code MongoDBContainer} starts a single-node replica set, which
 * matches how MongoDB is run in docker-compose. That is deliberate: a standalone
 * {@code mongod} cannot perform multi-document transactions, and the failure appears at
 * runtime rather than at startup.
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
class MapServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("rejects a request with no token")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/map/ping"))
                .andExpect(status().isUnauthorized())
                // The shared ApiError envelope, produced by the entry point in common.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/map/ping"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("accepts a valid token and resolves identity from its claims")
    void acceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/map/ping")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("507f1f77bcf86cd799439011")
                                        .claim("email", "brian@konradlorenz.edu.co")
                                        .claim("roles", List.of("ROLE_STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("map-service"))
                .andExpect(jsonPath("$.userId").value("507f1f77bcf86cd799439011"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_STUDENT"));
    }

    @Test
    @DisplayName("leaves the health probe open so Docker can check it")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("returns the shared error envelope for an unknown path")
    void unknownPathUsesSharedEnvelope() throws Exception {
        mockMvc.perform(get("/api/map/does-not-exist")
                        .with(jwt().jwt(b -> b.subject("u1").claim("roles", List.of("ROLE_ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("Mongock created the baseline indexes")
    void mongockRanMigrations() {
        var indexNames = StreamSupport
                .stream(mongoTemplate.indexOps("spaces").getIndexInfo().spliterator(), false)
                .map(info -> info.getName())
                .toList();

        assertThat(indexNames).contains("uk_spaces_building_code", "ix_spaces_code", "tx_spaces_search",
                "ix_spaces_building_floor");
    }
}
