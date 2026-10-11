package co.edu.konradlorenz.kapp.semaphore.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The exhaustive authorization matrix for every endpoint this service publishes, against every
 * role and against no token at all, as {@code docs/api/semaphore.openapi.yaml} 2.0 states it:
 *
 * <ul>
 *   <li>catalog reads: any authenticated role except {@code ROLE_GUEST}, staff included;</li>
 *   <li>the catalog import: {@code ROLE_ADMIN} only;</li>
 *   <li>the semáforo and the plans: {@code ROLE_STUDENT} only, always the caller's own.</li>
 * </ul>
 *
 * <p>Every (endpoint, caller) pair is its own test method with exactly one assertion, so a
 * regression names the exact broken combination. A caller who is allowed but asks for something
 * that is not there - a plan that does not exist, an empty import - gets that answer, 404 or 400,
 * never a 401 or 403: that is what being let through looks like.
 *
 * <p>Write endpoints get a body valid under {@code @Valid} even where refusal is expected:
 * argument binding runs before {@code @PreAuthorize}, so an invalid body would answer 400 whoever
 * sent it and hide the outcome this class exists to prove.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthorizationMatrixTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    private static final String SEEDED_PENSUM = "1015";
    private static final String SEEDED_PROGRAM = "506";
    private static final String SEEDED_COURSE = "11015";
    private static final String SEEDED_ELECTIVE = "59098";
    private static final String NO_SUCH_PLAN = "000000000000000000000000";
    private static final String PLAN_JSON = "{\"name\":\"Mi plan\",\"pensumCode\":\"1015\"}";

    // ==================================================================================
    // Catalog
    // ==================================================================================

    @Test
    @DisplayName("listPrograms: anonymous is refused with 401")
    void listPrograms_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/programs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listPrograms: GUEST is refused with 403")
    void listPrograms_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/programs").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listPrograms: STUDENT is allowed")
    void listPrograms_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs").with(student("listPrograms-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPrograms: PROFESSOR is allowed")
    void listPrograms_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs").with(professor("listPrograms-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPrograms: STAFF is allowed")
    void listPrograms_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs").with(staff("listPrograms-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPrograms: ADMIN is allowed")
    void listPrograms_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs").with(admin("listPrograms-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getProgram: anonymous is refused with 401")
    void getProgram_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getProgram: GUEST is refused with 403")
    void getProgram_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getProgram: STUDENT is allowed")
    void getProgram_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(student("getProgram-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getProgram: PROFESSOR is allowed")
    void getProgram_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(professor("getProgram-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getProgram: STAFF is allowed")
    void getProgram_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(staff("getProgram-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getProgram: ADMIN is allowed")
    void getProgram_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(admin("getProgram-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensums: anonymous is refused with 401")
    void listPensums_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listPensums: GUEST is refused with 403")
    void listPensums_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listPensums: STUDENT is allowed")
    void listPensums_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(student("listPensums-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensums: PROFESSOR is allowed")
    void listPensums_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(professor("listPensums-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensums: STAFF is allowed")
    void listPensums_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(staff("listPensums-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensums: ADMIN is allowed")
    void listPensums_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(admin("listPensums-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getPensum: anonymous is refused with 401")
    void getPensum_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getPensum: GUEST is refused with 403")
    void getPensum_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getPensum: STUDENT is allowed")
    void getPensum_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(student("getPensum-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getPensum: PROFESSOR is allowed")
    void getPensum_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(professor("getPensum-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getPensum: STAFF is allowed")
    void getPensum_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(staff("getPensum-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getPensum: ADMIN is allowed")
    void getPensum_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(admin("getPensum-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumCourses: anonymous is refused with 401")
    void listPensumCourses_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listPensumCourses: GUEST is refused with 403")
    void listPensumCourses_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listPensumCourses: STUDENT is allowed")
    void listPensumCourses_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM).with(student("listPensumCourses-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumCourses: PROFESSOR is allowed")
    void listPensumCourses_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM).with(professor("listPensumCourses-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumCourses: STAFF is allowed")
    void listPensumCourses_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM).with(staff("listPensumCourses-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumCourses: ADMIN is allowed")
    void listPensumCourses_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM).with(admin("listPensumCourses-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumElectives: anonymous is refused with 401")
    void listPensumElectives_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listPensumElectives: GUEST is refused with 403")
    void listPensumElectives_guest_403() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listPensumElectives: STUDENT is allowed")
    void listPensumElectives_student_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM).with(student("listPensumElectives-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumElectives: PROFESSOR is allowed")
    void listPensumElectives_professor_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM).with(professor("listPensumElectives-professor")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumElectives: STAFF is allowed")
    void listPensumElectives_staff_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM).with(staff("listPensumElectives-staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listPensumElectives: ADMIN is allowed")
    void listPensumElectives_admin_200() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM).with(admin("listPensumElectives-admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("importPensums: anonymous is refused with 401")
    void importPensums_anonymous_401() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("importPensums: GUEST is refused with 403")
    void importPensums_guest_403() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("importPensums: STUDENT is refused with 403")
    void importPensums_student_403() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true").with(student("importPensums-student")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("importPensums: PROFESSOR is refused with 403")
    void importPensums_professor_403() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true").with(professor("importPensums-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("importPensums: STAFF is refused with 403")
    void importPensums_staff_403() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true").with(staff("importPensums-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("importPensums: ADMIN is let through (400)")
    void importPensums_admin_400() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import").file(emptyCsv()).param("dryRun", "true").with(admin("importPensums-admin")))
                .andExpect(status().isBadRequest());
    }

    // ==================================================================================
    // Student progress
    // ==================================================================================

    @Test
    @DisplayName("getMySemaphore: anonymous is refused with 401")
    void getMySemaphore_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getMySemaphore: GUEST is refused with 403")
    void getMySemaphore_guest_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphore: STUDENT is allowed")
    void getMySemaphore_student_200() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(student("getMySemaphore-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getMySemaphore: PROFESSOR is refused with 403")
    void getMySemaphore_professor_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(professor("getMySemaphore-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphore: STAFF is refused with 403")
    void getMySemaphore_staff_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(staff("getMySemaphore-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphore: ADMIN is refused with 403")
    void getMySemaphore_admin_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me").with(admin("getMySemaphore-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: anonymous is refused with 401")
    void getMySemaphoreSummary_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: GUEST is refused with 403")
    void getMySemaphoreSummary_guest_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: STUDENT is allowed")
    void getMySemaphoreSummary_student_200() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(student("getMySemaphoreSummary-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: PROFESSOR is refused with 403")
    void getMySemaphoreSummary_professor_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(professor("getMySemaphoreSummary-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: STAFF is refused with 403")
    void getMySemaphoreSummary_staff_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(staff("getMySemaphoreSummary-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMySemaphoreSummary: ADMIN is refused with 403")
    void getMySemaphoreSummary_admin_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/summary").with(admin("getMySemaphoreSummary-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyEligibleCourses: anonymous is refused with 401")
    void listMyEligibleCourses_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listMyEligibleCourses: GUEST is refused with 403")
    void listMyEligibleCourses_guest_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyEligibleCourses: STUDENT is allowed")
    void listMyEligibleCourses_student_200() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(student("listMyEligibleCourses-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listMyEligibleCourses: PROFESSOR is refused with 403")
    void listMyEligibleCourses_professor_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(professor("listMyEligibleCourses-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyEligibleCourses: STAFF is refused with 403")
    void listMyEligibleCourses_staff_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(staff("listMyEligibleCourses-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyEligibleCourses: ADMIN is refused with 403")
    void listMyEligibleCourses_admin_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/eligible").with(admin("listMyEligibleCourses-admin")))
                .andExpect(status().isForbidden());
    }

    // ==================================================================================
    // Academic plans
    // ==================================================================================

    @Test
    @DisplayName("listMyAcademicPlans: anonymous is refused with 401")
    void listMyAcademicPlans_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listMyAcademicPlans: GUEST is refused with 403")
    void listMyAcademicPlans_guest_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyAcademicPlans: STUDENT is allowed")
    void listMyAcademicPlans_student_200() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(student("listMyAcademicPlans-student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listMyAcademicPlans: PROFESSOR is refused with 403")
    void listMyAcademicPlans_professor_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(professor("listMyAcademicPlans-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyAcademicPlans: STAFF is refused with 403")
    void listMyAcademicPlans_staff_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(staff("listMyAcademicPlans-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listMyAcademicPlans: ADMIN is refused with 403")
    void listMyAcademicPlans_admin_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(admin("listMyAcademicPlans-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createMyAcademicPlan: anonymous is refused with 401")
    void createMyAcademicPlan_anonymous_401() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("createMyAcademicPlan: GUEST is refused with 403")
    void createMyAcademicPlan_guest_403() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createMyAcademicPlan: STUDENT is allowed")
    void createMyAcademicPlan_student_201() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON).with(student("createMyAcademicPlan-student")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("createMyAcademicPlan: PROFESSOR is refused with 403")
    void createMyAcademicPlan_professor_403() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON).with(professor("createMyAcademicPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createMyAcademicPlan: STAFF is refused with 403")
    void createMyAcademicPlan_staff_403() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON).with(staff("createMyAcademicPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createMyAcademicPlan: ADMIN is refused with 403")
    void createMyAcademicPlan_admin_403() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON).with(admin("createMyAcademicPlan-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMyAcademicPlan: anonymous is refused with 401")
    void getMyAcademicPlan_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getMyAcademicPlan: GUEST is refused with 403")
    void getMyAcademicPlan_guest_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMyAcademicPlan: STUDENT is let through (404)")
    void getMyAcademicPlan_student_404() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(student("getMyAcademicPlan-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getMyAcademicPlan: PROFESSOR is refused with 403")
    void getMyAcademicPlan_professor_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(professor("getMyAcademicPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMyAcademicPlan: STAFF is refused with 403")
    void getMyAcademicPlan_staff_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(staff("getMyAcademicPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getMyAcademicPlan: ADMIN is refused with 403")
    void getMyAcademicPlan_admin_403() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(admin("getMyAcademicPlan-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: anonymous is refused with 401")
    void updateMyAcademicPlan_anonymous_401() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: GUEST is refused with 403")
    void updateMyAcademicPlan_guest_403() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: STUDENT is let through (404)")
    void updateMyAcademicPlan_student_404() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}").with(student("updateMyAcademicPlan-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: PROFESSOR is refused with 403")
    void updateMyAcademicPlan_professor_403() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}").with(professor("updateMyAcademicPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: STAFF is refused with 403")
    void updateMyAcademicPlan_staff_403() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}").with(staff("updateMyAcademicPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateMyAcademicPlan: ADMIN is refused with 403")
    void updateMyAcademicPlan_admin_403() throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}").with(admin("updateMyAcademicPlan-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: anonymous is refused with 401")
    void deleteMyAcademicPlan_anonymous_401() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: GUEST is refused with 403")
    void deleteMyAcademicPlan_guest_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: STUDENT is let through (404)")
    void deleteMyAcademicPlan_student_404() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(student("deleteMyAcademicPlan-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: PROFESSOR is refused with 403")
    void deleteMyAcademicPlan_professor_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(professor("deleteMyAcademicPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: STAFF is refused with 403")
    void deleteMyAcademicPlan_staff_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(staff("deleteMyAcademicPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteMyAcademicPlan: ADMIN is refused with 403")
    void deleteMyAcademicPlan_admin_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", NO_SUCH_PLAN).with(admin("deleteMyAcademicPlan-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: anonymous is refused with 401")
    void placeCourseInMyPlan_anonymous_401() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: GUEST is refused with 403")
    void placeCourseInMyPlan_guest_403() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}").with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: STUDENT is let through (404)")
    void placeCourseInMyPlan_student_404() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}").with(student("placeCourseInMyPlan-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: PROFESSOR is refused with 403")
    void placeCourseInMyPlan_professor_403() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}").with(professor("placeCourseInMyPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: STAFF is refused with 403")
    void placeCourseInMyPlan_staff_403() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}").with(staff("placeCourseInMyPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("placeCourseInMyPlan: ADMIN is refused with 403")
    void placeCourseInMyPlan_admin_403() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{\"plannedLevel\":3}").with(admin("placeCourseInMyPlan-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: anonymous is refused with 401")
    void resetCourseInMyPlan_anonymous_401() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: GUEST is refused with 403")
    void resetCourseInMyPlan_guest_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).with(guest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: STUDENT is let through (404)")
    void resetCourseInMyPlan_student_404() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).with(student("resetCourseInMyPlan-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: PROFESSOR is refused with 403")
    void resetCourseInMyPlan_professor_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).with(professor("resetCourseInMyPlan-professor")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: STAFF is refused with 403")
    void resetCourseInMyPlan_staff_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).with(staff("resetCourseInMyPlan-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resetCourseInMyPlan: ADMIN is refused with 403")
    void resetCourseInMyPlan_admin_403() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{item}", NO_SUCH_PLAN, SEEDED_COURSE).with(admin("resetCourseInMyPlan-admin")))
                .andExpect(status().isForbidden());
    }

    // ==================================================================================
    // What 2.0 removed stays removed: the catalog's writes, marking a course or an
    // elective by hand, and reading somebody else's semáforo.
    // ==================================================================================

    @Test
    @DisplayName("createProgram: anonymous is refused with 401")
    void createProgram_anonymous_401() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("createProgram: STUDENT is gone (405)")
    void createProgram_student_405() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").contentType(MediaType.APPLICATION_JSON).content("{}").with(student("createProgram-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("createProgram: ADMIN is gone (405)")
    void createProgram_admin_405() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("createProgram-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("replaceProgram: anonymous is refused with 401")
    void replaceProgram_anonymous_401() throws Exception {
        mockMvc.perform(put("/api/catalog/programs/{code}", SEEDED_PROGRAM).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("replaceProgram: STUDENT is gone (405)")
    void replaceProgram_student_405() throws Exception {
        mockMvc.perform(put("/api/catalog/programs/{code}", SEEDED_PROGRAM).contentType(MediaType.APPLICATION_JSON).content("{}").with(student("replaceProgram-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("replaceProgram: ADMIN is gone (405)")
    void replaceProgram_admin_405() throws Exception {
        mockMvc.perform(put("/api/catalog/programs/{code}", SEEDED_PROGRAM).contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("replaceProgram-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("deleteProgram: anonymous is refused with 401")
    void deleteProgram_anonymous_401() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", SEEDED_PROGRAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleteProgram: STUDENT is gone (405)")
    void deleteProgram_student_405() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(student("deleteProgram-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("deleteProgram: ADMIN is gone (405)")
    void deleteProgram_admin_405() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(admin("deleteProgram-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("createPensum: anonymous is refused with 401")
    void createPensum_anonymous_401() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("createPensum: STUDENT is gone (405)")
    void createPensum_student_405() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").contentType(MediaType.APPLICATION_JSON).content("{}").with(student("createPensum-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("createPensum: ADMIN is gone (405)")
    void createPensum_admin_405() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("createPensum-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("replacePensum: anonymous is refused with 401")
    void replacePensum_anonymous_401() throws Exception {
        mockMvc.perform(put("/api/catalog/pensums/{code}", SEEDED_PENSUM).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("replacePensum: STUDENT is gone (405)")
    void replacePensum_student_405() throws Exception {
        mockMvc.perform(put("/api/catalog/pensums/{code}", SEEDED_PENSUM).contentType(MediaType.APPLICATION_JSON).content("{}").with(student("replacePensum-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("replacePensum: ADMIN is gone (405)")
    void replacePensum_admin_405() throws Exception {
        mockMvc.perform(put("/api/catalog/pensums/{code}", SEEDED_PENSUM).contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("replacePensum-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("deletePensum: anonymous is refused with 401")
    void deletePensum_anonymous_401() throws Exception {
        mockMvc.perform(delete("/api/catalog/pensums/{code}", SEEDED_PENSUM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deletePensum: STUDENT is gone (405)")
    void deletePensum_student_405() throws Exception {
        mockMvc.perform(delete("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(student("deletePensum-student")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("deletePensum: ADMIN is gone (405)")
    void deletePensum_admin_405() throws Exception {
        mockMvc.perform(delete("/api/catalog/pensums/{code}", SEEDED_PENSUM).with(admin("deletePensum-admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("updateMyCourseStatus: anonymous is refused with 401")
    void updateMyCourseStatus_anonymous_401() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/courses/{code}", SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("updateMyCourseStatus: STUDENT is gone (404)")
    void updateMyCourseStatus_student_404() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/courses/{code}", SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{}").with(student("updateMyCourseStatus-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateMyCourseStatus: ADMIN is gone (404)")
    void updateMyCourseStatus_admin_404() throws Exception {
        mockMvc.perform(put("/api/semaphore/me/courses/{code}", SEEDED_COURSE).contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("updateMyCourseStatus-admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resolveMyElective: anonymous is refused with 401")
    void resolveMyElective_anonymous_401() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resolveMyElective: STUDENT is gone (404)")
    void resolveMyElective_student_404() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE).contentType(MediaType.APPLICATION_JSON).content("{}").with(student("resolveMyElective-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("resolveMyElective: ADMIN is gone (404)")
    void resolveMyElective_admin_404() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE).contentType(MediaType.APPLICATION_JSON).content("{}").with(admin("resolveMyElective-admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("clearMyElective: anonymous is refused with 401")
    void clearMyElective_anonymous_401() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("clearMyElective: STUDENT is gone (404)")
    void clearMyElective_student_404() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE).with(student("clearMyElective-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("clearMyElective: ADMIN is gone (404)")
    void clearMyElective_admin_404() throws Exception {
        mockMvc.perform(delete("/api/semaphore/me/electives/{item}", SEEDED_ELECTIVE).with(admin("clearMyElective-admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getStudentSemaphore: anonymous is refused with 401")
    void getStudentSemaphore_anonymous_401() throws Exception {
        mockMvc.perform(get("/api/semaphore/{userId}", "someone-else"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getStudentSemaphore: STUDENT is gone (404)")
    void getStudentSemaphore_student_404() throws Exception {
        mockMvc.perform(get("/api/semaphore/{userId}", "someone-else").with(student("getStudentSemaphore-student")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getStudentSemaphore: ADMIN is gone (404)")
    void getStudentSemaphore_admin_404() throws Exception {
        mockMvc.perform(get("/api/semaphore/{userId}", "someone-else").with(admin("getStudentSemaphore-admin")))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------------------------

    private static MockMultipartFile emptyCsv() {
        return new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
    }

    private static RequestPostProcessor guest() {
        return jwtWithRole("matrix-guest", "ROLE_GUEST");
    }

    private static RequestPostProcessor student(String subject) {
        return jwtWithRole(subject, "ROLE_STUDENT");
    }

    private static RequestPostProcessor professor(String subject) {
        return jwtWithRole(subject, "ROLE_PROFESSOR");
    }

    private static RequestPostProcessor staff(String subject) {
        return jwtWithRole(subject, "ROLE_STAFF");
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
