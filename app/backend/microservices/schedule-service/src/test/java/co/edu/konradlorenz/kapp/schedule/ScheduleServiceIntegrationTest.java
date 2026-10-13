package co.edu.konradlorenz.kapp.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The skeleton holds together: the shared security auto-configuration from {@code common}, role
 * mapping from the {@code roles} claim, the open health probe and the shared error envelope. The
 * service stores nothing, so it starts without any database.
 */
class ScheduleServiceIntegrationTest extends AbstractScheduleServiceTest {

    @Test
    @DisplayName("rejects a request with no token")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/schedule/ping"))
                .andExpect(status().isUnauthorized())
                // The shared ApiError envelope, produced by the entry point in common.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/schedule/ping"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("accepts a valid token and resolves identity from its claims")
    void acceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/schedule/ping")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject(STUDENT_ID)
                                        .claim("email", "pepito.perez@konradlorenz.edu.co")
                                        .claim("roles", List.of("ROLE_STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("schedule-service"))
                .andExpect(jsonPath("$.userId").value(STUDENT_ID))
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
        mockMvc.perform(get("/api/schedule/does-not-exist")
                        .with(jwt().jwt(b -> b.subject("u1").claim("roles", List.of("ROLE_ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
