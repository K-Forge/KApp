package co.edu.konradlorenz.kapp.schedule;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.catalog.CatalogClient;
import co.edu.konradlorenz.kapp.schedule.catalog.PensumCourseView;
import co.edu.konradlorenz.kapp.schedule.map.MapBuildingView;
import co.edu.konradlorenz.kapp.schedule.map.MapClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared fixture for the schedule tests: one application context, a fixed clock, and the two
 * neighbours mocked.
 *
 * <p>The test SINU serves the invented timetable of {@code docs/api/sinu/example.json}, moved onto
 * the current period. The clock stands on Monday 5 October 2026, week 11 of 2026-2, so every test
 * sees the same classes on the same dates. Nothing is stored, so there is no database to start.
 *
 * <p>{@link CatalogClient} and {@link MapClient} are Mockito mocks rather than real neighbours: both
 * lookups fail open, and the tests say what the neighbours answer. By default the map says the
 * Edificio Central is SINU's "Sede Principal", and the catalogue has five of the six level-5 courses
 * the example's student takes, each under its own code; Inglés Proficiencia is missing on purpose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractScheduleServiceTest.FixedClock.class)
public abstract class AbstractScheduleServiceTest {

    /** Monday 5 October 2026, ten in the morning in Bogotá. */
    protected static final Instant NOW = Instant.parse("2026-10-05T15:00:00Z");

    protected static final String STUDENT_ID = "507f1f77bcf86cd799439011";
    protected static final String PROFESSOR_ID = "507f1f77bcf86cd799439022";

    /** The profile role of administrative staff, which takes and teaches no classes. */
    protected static final String ROLE_STAFF = "ROLE_STAFF";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("America/Bogota"));
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private CacheManager caches;

    @MockitoBean
    protected CatalogClient catalogClient;

    @MockitoBean
    protected MapClient mapClient;

    @BeforeEach
    void neighbours() {
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        when(mapClient.listBuildings()).thenReturn(List.of(
                new MapBuildingView("EC", List.of("Sede Principal")),
                new MapBuildingView("BI", List.of())));
        when(catalogClient.listPensumCourses("1015")).thenReturn(List.of(
                course("13013", "Ecuaciones Diferenciales"),
                course("46033", "Sistemas Operacionales"),
                course("31404", "Diseño de Interfaces de usuario"),
                course("31614", "Nuevas Tecnologías de Desarrollo"),
                course("46012", "Bases de Datos II")));
    }

    private static PensumCourseView course(String code, String name) {
        return new PensumCourseView(code, code, code, name);
    }

    // ---------------------------------------------------------------------------------
    // Callers
    // ---------------------------------------------------------------------------------

    protected static RequestPostProcessor callerWith(String userId, String... roles) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId)
                        .claim("email", "caller-" + userId + "@konradlorenz.edu.co")
                        .claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }

    protected static RequestPostProcessor student(String userId) {
        return callerWith(userId, KappRoles.STUDENT);
    }

    protected static RequestPostProcessor professor(String userId) {
        return callerWith(userId, KappRoles.PROFESSOR);
    }

    protected static RequestPostProcessor staff(String userId) {
        return callerWith(userId, ROLE_STAFF);
    }

    protected static RequestPostProcessor admin(String userId) {
        return callerWith(userId, KappRoles.ADMIN);
    }

    protected static RequestPostProcessor guest(String userId) {
        return callerWith(userId, KappRoles.GUEST);
    }
}
