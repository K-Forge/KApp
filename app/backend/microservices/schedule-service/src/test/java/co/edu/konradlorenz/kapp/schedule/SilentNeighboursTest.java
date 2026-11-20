package co.edu.konradlorenz.kapp.schedule;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The timetable with both neighbours up but silent: semaphore-service and map-service take the
 * connection and never answer, as a hung service on the dev server did (#155). The real Feign
 * clients are used, not mocks, so this is what proves their timeouts are the ones configured: the
 * week still comes back, without pensum items or buildings, well inside the gateway's 10 s.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractScheduleServiceTest.FixedClock.class)
class SilentNeighboursTest {

    /** Accepts connections into its backlog and never reads or writes. */
    private static final ServerSocket SILENT = silent();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void neighbours(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + SILENT.getLocalPort();
        registry.add("kapp.schedule.semaphore-service-url", () -> url);
        registry.add("kapp.schedule.map-service-url", () -> url);
    }

    @AfterAll
    static void close() throws IOException {
        SILENT.close();
    }

    @Test
    @DisplayName("a week is served inside the gateway's timeout while both neighbours hang")
    void weekIsServedWhileNeighboursHang() {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
                mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-10-05")
                                .with(jwt().jwt(token -> token.subject(AbstractScheduleServiceTest.STUDENT_ID)
                                                .claim("email", "estudiante@konradlorenz.edu.co")
                                                .claim("roles", List.of(KappRoles.STUDENT)))
                                        .authorities(new SimpleGrantedAuthority(KappRoles.STUDENT))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.days.MONDAY[0].sinuCode").value("13013"))
                        .andExpect(jsonPath("$.days.MONDAY[0].pensumItemCode").value(nullValue()))
                        .andExpect(jsonPath("$.days.MONDAY[0].buildingCode").value(nullValue())));
    }

    private static ServerSocket silent() {
        try {
            return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
