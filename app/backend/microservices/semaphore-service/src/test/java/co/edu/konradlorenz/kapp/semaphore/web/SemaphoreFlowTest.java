package co.edu.konradlorenz.kapp.semaphore.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The semáforo end to end against the test SINU: the invented student of
 * {@code docs/api/sinu/example.json} laid over pensum 1015 as the change units seed it, on Monday
 * 5 October 2026. Levels 1 to 4 were taken - Electromagnetismo postponed and Lógica Digital lost
 * among them - and level 5 is in progress in 2026-2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SemaphoreFlowTest.FixedClock.class)
class SemaphoreFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    /** Monday 5 October 2026, ten in the morning in Bogotá. */
    static final Instant NOW = Instant.parse("2026-10-05T15:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("America/Bogota"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static String item(String pensumItemCode) {
        return "$.courses[?(@.pensumItemCode=='" + pensumItemCode + "')]";
    }

    @Test
    @DisplayName("the semáforo carries the student's program, pensum and level, says it is test data, and mirrors the pensum")
    void semaphoreHeader() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-header")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("flow-header"))
                .andExpect(jsonPath("$.programCode").value("506"))
                .andExpect(jsonPath("$.pensumCode").value("1015"))
                .andExpect(jsonPath("$.currentLevel").value(5))
                .andExpect(jsonPath("$.source").value("TEST"))
                .andExpect(jsonPath("$.readAt").value("2026-10-05T15:00:00Z"))
                .andExpect(jsonPath("$.courses.length()").value(51))
                .andExpect(jsonPath("$.courses[0].pensumItemCode").value("41025"))
                .andExpect(jsonPath("$.studentCode").doesNotExist())
                .andExpect(jsonPath("$.reconciliation").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("a passed course carries SINU's status, its period moved onto today's calendar, and its grade")
    void passedCourse() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-passed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item("11015") + ".status", contains("PASSED")))
                .andExpect(jsonPath(item("11015") + ".sinuStatus", contains("APROBADA")))
                .andExpect(jsonPath(item("11015") + ".period", contains("20242")))
                .andExpect(jsonPath(item("11015") + ".grade", contains(42)));
    }

    @Test
    @DisplayName("postponed, lost and in-progress courses come through as such, with SINU's own words")
    void otherStatuses() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-statuses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item("54044") + ".status", contains("POSTPONED")))
                .andExpect(jsonPath(item("54044") + ".sinuStatus", contains("APLAZADA")))
                .andExpect(jsonPath(item("31506") + ".status", contains("FAILED")))
                .andExpect(jsonPath(item("31506") + ".grade", contains(24)))
                .andExpect(jsonPath(item("13013") + ".status", contains("IN_PROGRESS")))
                .andExpect(jsonPath(item("13013") + ".sinuStatus", contains("EN CURSO")))
                .andExpect(jsonPath(item("13013") + ".period", contains("20262")));
    }

    @Test
    @DisplayName("an item SINU has no record of is PENDING, with nulls written rather than left out")
    void pendingCourse() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-pending")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item("17018") + ".status", contains("PENDING")))
                .andExpect(jsonPath(item("17018") + ".sinuStatus", contains((Object) null)))
                .andExpect(jsonPath(item("17018") + ".period", contains((Object) null)))
                .andExpect(jsonPath(item("59075") + ".resolvedSinuCode", contains((Object) null)))
                .andExpect(jsonPath(item("59075") + ".resolvedName", contains((Object) null)));
    }

    @Test
    @DisplayName("the summary counts what SINU says and takes the level SINU has")
    void summary() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(student("flow-summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsPassed").value(59))
                .andExpect(jsonPath("$.creditsInProgress").value(16))
                .andExpect(jsonPath("$.creditsRemaining").value(68))
                .andExpect(jsonPath("$.totalCredits").value(143))
                .andExpect(jsonPath("$.percentComplete").value(41.3))
                .andExpect(jsonPath("$.currentLevel").value(5))
                .andExpect(jsonPath("$.byArea[0].area").value("CB"))
                .andExpect(jsonPath("$.byArea[0].creditsPassed").value(24))
                .andExpect(jsonPath("$.byArea[0].creditsTotal").value(36));
    }

    @Test
    @DisplayName("eligible lists the pending items whose prerequisites were all passed, and nothing else")
    void eligible() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(student("flow-eligible")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pensumItemCode", containsInAnyOrder(
                        "9a032", "59075", "59078", "59085", "59088", "40012", "59035", "59096", "59098",
                        "56201", "59045", "4a033", "9a020")))
                // Its prerequisite, Ecuaciones Diferenciales, is still in progress.
                .andExpect(jsonPath("$[*].pensumItemCode", not(hasItem("17018"))))
                // Its prerequisite, Lógica Digital, was lost.
                .andExpect(jsonPath("$[*].pensumItemCode", not(hasItem("31750"))))
                // Taken again, not for the first time.
                .andExpect(jsonPath("$[*].pensumItemCode", not(hasItem("54044"))));
    }

    @Test
    @DisplayName("nobody but a student reads a semáforo")
    void onlyStudents() throws Exception {
        for (String role : List.of("ROLE_PROFESSOR", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_GUEST")) {
            mockMvc.perform(get("/api/semaphore/me").with(jwtWithRole("flow-" + role, role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/semaphore/me")).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor student(String subject) {
        return jwtWithRole(subject, "ROLE_STUDENT");
    }

    private static RequestPostProcessor jwtWithRole(String subject, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(b -> b.subject(subject)
                        .claim("email", subject + "@konradlorenz.edu.co")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
