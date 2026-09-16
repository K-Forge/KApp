package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.semaphore.client.UserProfileClient;
import co.edu.konradlorenz.kapp.semaphore.client.UserProfileResponse;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.repository.StudentProgressRepository;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumAreaDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the student-facing flows that a unit test cannot exercise
 * meaningfully on its own: lazy creation over real HTTP and MongoDB, the genuine
 * concurrent race two simultaneous first calls create, grade range validation as
 * enforced by {@code @Valid} (not the service's own cross-field rules, which
 * {@code StudentProgressServiceTest} already covers), and reconciliation after an admin
 * edits a pinned pensum.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class StudentProgressFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private StudentProgressRepository progressRepository;

    @MockitoBean
    private UserProfileClient userProfileClient;

    private static final String SEEDED_PENSUM = "1015";
    private static final String SEEDED_PROGRAM = "506";

    @Test
    @DisplayName("first GET /me lazily materialises every seeded pensum item as PENDING, in sync")
    void lazyCreationMaterialisesTheWholePinnedPensum() throws Exception {
        stubProfile("flow-lazy-student", SEEDED_PROGRAM, "506999999", 1);

        mockMvc.perform(get("/api/semaphore/me").with(student("flow-lazy-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pensumCode").value(SEEDED_PENSUM))
                .andExpect(jsonPath("$.programCode").value(SEEDED_PROGRAM))
                .andExpect(jsonPath("$.studentCode").value("506999999"))
                .andExpect(jsonPath("$.courses.length()").value(51))
                .andExpect(jsonPath("$.courses[0].status").value("PENDING"))
                .andExpect(jsonPath("$.reconciliation.inSync").value(true))
                .andExpect(jsonPath("$.reconciliation.addedCourses.length()").value(0))
                .andExpect(jsonPath("$.reconciliation.removedCourses.length()").value(0));
    }

    @Test
    @DisplayName("a second GET for the same student returns the same document, not a new one")
    void secondCallReturnsTheSameDocument() throws Exception {
        stubProfile("flow-idempotent-student", SEEDED_PROGRAM, "506232731", 1);

        mockMvc.perform(get("/api/semaphore/me").with(student("flow-idempotent-student")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-idempotent-student")))
                .andExpect(status().isOk());

        assertThat(progressRepository.findByUserId("flow-idempotent-student")).isPresent();
        long count = progressRepository.findAll().stream()
                .filter(p -> p.userId().equals("flow-idempotent-student"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent first GETs for the same student produce exactly one document")
    void concurrentFirstReadsProduceExactlyOneDocument() throws Exception {
        String userId = "flow-concurrent-student";
        stubProfile(userId, SEEDED_PROGRAM, "506232732", 1);

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger okCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    var result = mockMvc.perform(get("/api/semaphore/me").with(student(userId)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        okCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("all racing requests completed").isTrue();
        assertThat(okCount.get()).as("every racing request still got a 200").isEqualTo(threadCount);

        long documentCount = progressRepository.findAll().stream()
                .filter(p -> p.userId().equals(userId))
                .count();
        assertThat(documentCount)
                .as("the unique {userId, pensumCode} index must collapse the race to one document")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a grade above 50 is rejected with 400")
    void gradeAboveFiftyIsRejected() throws Exception {
        stubProfile("flow-grade-high-student", SEEDED_PROGRAM, "506232733", 1);
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-grade-high-student")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/semaphore/me/courses/{code}", "11015")
                        .with(student("flow-grade-high-student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PASSED","grade":51,"period":"20261"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("grade"));
    }

    @Test
    @DisplayName("a grade below 0 is rejected with 400")
    void gradeBelowZeroIsRejected() throws Exception {
        stubProfile("flow-grade-low-student", SEEDED_PROGRAM, "506232734", 1);
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-grade-low-student")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/semaphore/me/courses/{code}", "11015")
                        .with(student("flow-grade-low-student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PASSED","grade":-1,"period":"20261"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("grade"));
    }

    @Test
    @DisplayName("a grade of exactly 50 (the top of the scale) is accepted")
    void gradeOfFiftyIsAccepted() throws Exception {
        stubProfile("flow-grade-boundary-student", SEEDED_PROGRAM, "506232735", 1);
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-grade-boundary-student")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/semaphore/me/courses/{code}", "11015")
                        .with(student("flow-grade-boundary-student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PASSED","grade":50,"period":"20261"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(50));
    }

    @Test
    @DisplayName("reconciliation reports an admin's pensum edit without losing existing progress")
    void reconciliationReportsAnAdminEdit() throws Exception {
        String dedicatedProgram = "999";
        String pensumCode = "RECON-TEST";

        mockMvc.perform(post("/api/catalog/pensums").with(admin("recon-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemPensumJson(pensumCode, dedicatedProgram)))
                .andExpect(status().isCreated());

        stubProfile("flow-recon-student", dedicatedProgram, "999000001", 1);

        // First read: lazily creates from the two-item pensum, fully in sync.
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-recon-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.reconciliation.inSync").value(true));

        // The admin drops item RB and adds item RC.
        mockMvc.perform(put("/api/catalog/pensums/{code}", pensumCode).with(admin("recon-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editedPensumJson(pensumCode, dedicatedProgram)))
                .andExpect(status().isOk());

        // The student's next read reports exactly that divergence and keeps RB's entry.
        mockMvc.perform(get("/api/semaphore/me").with(student("flow-recon-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliation.inSync").value(false))
                .andExpect(jsonPath("$.reconciliation.addedCourses[0]").value("RC"))
                .andExpect(jsonPath("$.reconciliation.removedCourses[0]").value("RB"))
                .andExpect(jsonPath("$.courses.length()").value(3)); // RA, RC kept + RB retained
    }

    // ---- helpers -----------------------------------------------------------------

    private void stubProfile(String subject, String programCode, String studentCode, int currentLevel) {
        when(userProfileClient.getMyProfile()).thenReturn(new UserProfileResponse(
                subject, "ROLE_STUDENT",
                new UserProfileResponse.Academic(studentCode, programCode, currentLevel)));
        // MockitoBean shares the same mock across every test method in this class, and
        // each test stubs a fresh answer for its own subject; this call is only ever
        // wired up right before the request(s) that need it.
    }

    private String twoItemPensumJson(String pensumCode, String programCode) throws Exception {
        PensumAreaDto area = new PensumAreaDto("GA", "General Area", "#539392", 6, 8);
        PensumCourseDto itemA = new PensumCourseDto(
                "RA", "RA", "Recon Item A", 1, 3, 4, null, "GA", false, List.of(), null);
        PensumCourseDto itemB = new PensumCourseDto(
                "RB", "RB", "Recon Item B", 1, 3, 4, null, "GA", false, List.of(), null);
        PensumDto dto = new PensumDto(pensumCode, programCode, "Recon Test Program",
                "Test Faculty", "Test Reform", PensumStatus.ACTIVE, 6, 8, 1,
                List.of(area), List.of(itemA, itemB));
        return mapper.writeValueAsString(dto);
    }

    private String editedPensumJson(String pensumCode, String programCode) throws Exception {
        PensumAreaDto area = new PensumAreaDto("GA", "General Area", "#539392", 6, 8);
        PensumCourseDto itemA = new PensumCourseDto(
                "RA", "RA", "Recon Item A", 1, 3, 4, null, "GA", false, List.of(), null);
        PensumCourseDto itemC = new PensumCourseDto(
                "RC", "RC", "Recon Item C", 1, 3, 4, null, "GA", false, List.of(), null);
        PensumDto dto = new PensumDto(pensumCode, programCode, "Recon Test Program",
                "Test Faculty", "Test Reform", PensumStatus.ACTIVE, 6, 8, 1,
                List.of(area), List.of(itemA, itemC));
        return mapper.writeValueAsString(dto);
    }

    private static RequestPostProcessor student(String subject) {
        return jwtWithRole(subject, "ROLE_STUDENT");
    }

    private static RequestPostProcessor admin(String subject) {
        return jwtWithRole(subject, "ROLE_ADMIN");
    }

    private static RequestPostProcessor jwtWithRole(String subject, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(b -> b.subject(subject)
                        .claim("email", subject + "@konradlorenz.edu.co")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
