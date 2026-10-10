package co.edu.konradlorenz.kapp.schedule;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.catalog.CatalogClient;
import co.edu.konradlorenz.kapp.schedule.domain.Enrollment;
import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Schedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared fixture for the schedule tests, mirroring {@code user-service}'s
 * {@code AbstractUserServiceTest}: one MongoDB, one application context, one set of seed
 * helpers.
 *
 * <p>{@link CatalogClient} is replaced with a Mockito mock rather than left to call
 * semaphore-service: it is not guaranteed to be up, and the contract only asks this service to fail open when it isn't - see
 * {@code catalog.PensumCatalogService}. Left unstubbed, the mock's default answer for
 * a {@code List}-returning method is an empty list, which is exactly the "catalogue not
 * reachable" case.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractScheduleServiceTest {

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected CatalogClient catalogClient;

    @BeforeEach
    void clearSchedules() {
        mongoTemplate.remove(new Query(), Schedule.class);
    }

    // ---------------------------------------------------------------------------------
    // Callers
    // ---------------------------------------------------------------------------------

    protected static RequestPostProcessor callerWith(String userId, String role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId)
                        .claim("email", userId + "@konradlorenz.edu.co")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    protected static RequestPostProcessor guest(String userId) {
        return callerWith(userId, KappRoles.GUEST);
    }

    protected static RequestPostProcessor student(String userId) {
        return callerWith(userId, KappRoles.STUDENT);
    }

    protected static RequestPostProcessor professor(String userId) {
        return callerWith(userId, KappRoles.PROFESSOR);
    }

    protected static RequestPostProcessor admin(String userId) {
        return callerWith(userId, KappRoles.ADMIN);
    }

    // ---------------------------------------------------------------------------------
    // Seed data
    // ---------------------------------------------------------------------------------

    protected Schedule save(Schedule schedule) {
        return mongoTemplate.save(schedule);
    }

    protected Schedule reload(String id) {
        return mongoTemplate.findById(id, Schedule.class);
    }

    protected static Schedule schedule(String userId, String period, boolean active,
                                       List<Enrollment> enrollments) {
        return new Schedule(UUID.randomUUID().toString(), userId, period, "506", "1015", 8,
                active, enrollments);
    }

    /** A level-6 Estadística Descriptiva enrollment, following the contract's own example. */
    protected static Enrollment estadistica(List<Meeting> meetings) {
        return new Enrollment(UUID.randomUUID().toString(), "17080", "2018", "ESTADISTICA DESCRIPTIVA",
                6, 3, 48, "51", null, "GÓMEZ RESTREPO LAURA MARCELA", "Sede Principal",
                LocalDate.parse("2026-07-27"), LocalDate.parse("2026-11-30"), "#539392", meetings);
    }

    protected static Meeting meeting(DayOfWeek dayOfWeek, String startTime, String endTime,
                                     MeetingPeriod... periods) {
        return new Meeting(UUID.randomUUID().toString(), dayOfWeek,
                LocalTime.parse(startTime), LocalTime.parse(endTime), List.of(periods));
    }

    protected static MeetingPeriod period(String from, String to, String room) {
        return new MeetingPeriod(LocalDate.parse(from), LocalDate.parse(to), room);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
