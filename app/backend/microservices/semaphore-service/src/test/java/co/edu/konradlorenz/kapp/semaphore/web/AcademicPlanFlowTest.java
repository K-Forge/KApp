package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.semaphore.client.UserProfileClient;
import co.edu.konradlorenz.kapp.semaphore.client.UserProfileResponse;
import co.edu.konradlorenz.kapp.semaphore.repository.AcademicPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The student's own academic plans: {@code /api/semaphore/me/plans}.
 *
 * <p>The rule this class exists to pin down is that <strong>moving a course is planning, not
 * passing it</strong>. {@code planningACourseEarlierDoesNotMakeItEligible} is the test that
 * matters; everything else is the machinery around it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class AcademicPlanFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AcademicPlanRepository plans;

    @MockitoBean
    private UserProfileClient userProfileClient;

    private static final String SEEDED_PENSUM = "1015";
    private static final String SEEDED_PROGRAM = "506";
    /** A first-level course of the seeded plan, so it is eligible from the start. */
    private static final String FIRST_LEVEL_COURSE = "11015";

    @AfterEach
    void clearPlans() {
        plans.deleteAll();
    }

    // ── Creating ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a new plan starts empty: nothing has been moved yet")
    void createStartsWithNoPlacements() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("Mi plan", SEEDED_PENSUM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mi plan"))
                .andExpect(jsonPath("$.pensumCode").value(SEEDED_PENSUM))
                .andExpect(jsonPath("$.placements.length()").value(0));
    }

    @Test
    @DisplayName("the first plan for a pensum becomes the primary one automatically")
    void firstPlanIsPrimary() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-first"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("Unico", SEEDED_PENSUM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    @DisplayName("a second plan is not primary: promoting is a deliberate act")
    void secondPlanIsNotPrimary() throws Exception {
        createPlan("p-second", "Primero");
        mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-second"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("Segundo", SEEDED_PENSUM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(false));
    }

    @Test
    @DisplayName("a plan over a pensum that does not exist is 404")
    void planOverUnknownPensumIs404() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-nopensum"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("Fantasma", "NO-EXISTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the eleventh plan for one pensum is refused with 409")
    void planCeilingIsEnforced() throws Exception {
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-many"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(planJson("Plan " + i, SEEDED_PENSUM)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/semaphore/me/plans").with(student("p-many"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("Plan 11", SEEDED_PENSUM)))
                .andExpect(status().isConflict());
    }

    // ── Listing and reading ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the listing puts the primary plan first, so the client never re-derives it")
    void listingIsOrderedPrimaryFirst() throws Exception {
        createPlan("p-order", "Primero");
        String second = createPlan("p-order", "Segundo");
        promote("p-order", second);

        mockMvc.perform(get("/api/semaphore/me/plans").with(student("p-order")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Segundo"))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    @DisplayName("a student sees only their own plans")
    void listingIsScopedToTheCaller() throws Exception {
        createPlan("p-mine", "Mio");
        createPlan("p-theirs", "Suyo");

        mockMvc.perform(get("/api/semaphore/me/plans").with(student("p-mine")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mio"));
    }

    @Test
    @DisplayName("reading somebody else's plan is 404, not 403: a 403 would confirm it exists")
    void readingAnotherStudentsPlanIs404() throws Exception {
        String other = createPlan("p-owner", "Privado");

        mockMvc.perform(get("/api/semaphore/me/plans/{id}", other).with(student("p-intruder")))
                .andExpect(status().isNotFound());
    }

    // ── Renaming and promoting ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a plan can be renamed")
    void renameWorks() throws Exception {
        String id = createPlan("p-rename", "Nombre viejo");

        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", id).with(student("p-rename"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre nuevo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre nuevo"));
    }

    @Test
    @DisplayName("renaming does not silently demote the plan")
    void renameKeepsPrimary() throws Exception {
        String id = createPlan("p-rename-primary", "Antes");

        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", id).with(student("p-rename-primary"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Despues\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    @DisplayName("promoting a plan demotes the one that held it")
    void promotingDemotesTheOther() throws Exception {
        String first = createPlan("p-promote", "Primero");
        String second = createPlan("p-promote", "Segundo");

        promote("p-promote", second);

        mockMvc.perform(get("/api/semaphore/me/plans/{id}", first).with(student("p-promote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(false));
    }

    @Test
    @DisplayName("a plan cannot be demoted directly: the student would be left with no primary")
    void demotingDirectlyIsRejected() throws Exception {
        String id = createPlan("p-demote", "Unico");

        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", id).with(student("p-demote"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primary\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("primary"));
    }

    @Test
    @DisplayName("an empty patch is 400, so a client bug does not look like a successful save")
    void emptyPatchIsRejected() throws Exception {
        String id = createPlan("p-empty", "Unico");

        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", id).with(student("p-empty"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Deleting ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting the primary plan promotes the next one")
    void deletingPrimaryPromotesTheNext() throws Exception {
        String first = createPlan("p-delete", "Primero");
        String second = createPlan("p-delete", "Segundo");

        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", first).with(student("p-delete")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/semaphore/me/plans/{id}", second).with(student("p-delete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    @DisplayName("deleting the last plan is allowed: the pensum as published is a valid state")
    void deletingTheLastPlanIsAllowed() throws Exception {
        String id = createPlan("p-last", "Unico");

        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", id).with(student("p-last")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/semaphore/me/plans").with(student("p-last")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("deleting somebody else's plan is 404 and leaves it standing")
    void deletingAnothersPlanIs404() throws Exception {
        String other = createPlan("p-victim", "Intacto");

        mockMvc.perform(delete("/api/semaphore/me/plans/{id}", other).with(student("p-attacker")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/semaphore/me/plans/{id}", other).with(student("p-victim")))
                .andExpect(status().isOk());
    }

    // ── Moving courses ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("moving a course records only that course, not the whole pensum")
    void placementStoresOnlyTheDelta() throws Exception {
        String id = createPlan("p-move", "Mi plan");

        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(student("p-move"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].code").value(FIRST_LEVEL_COURSE))
                .andExpect(jsonPath("$.placements[0].plannedLevel").value(7));
    }

    @Test
    @DisplayName("moving the same course again replaces the placement rather than adding one")
    void placementIsIdempotent() throws Exception {
        String id = createPlan("p-idem", "Mi plan");
        place("p-idem", id, FIRST_LEVEL_COURSE, 7);

        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(student("p-idem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1))
                .andExpect(jsonPath("$.placements[0].plannedLevel").value(9));
    }

    /**
     * The nominal load is six. Students take more, and a cap here would refuse a real
     * timetable, so nine are placed at one level deliberately.
     */
    @Test
    @DisplayName("there is no cap on how many courses may sit at one level")
    void manyCoursesMaySitAtOneLevel() throws Exception {
        String id = createPlan("p-nocap", "Semestre pesado");
        List<String> codes = allPensumCodes("p-nocap").stream().limit(9).toList();
        assertThat(codes).hasSize(9);

        for (String code : codes) {
            place("p-nocap", id, code, 3);
        }

        mockMvc.perform(get("/api/semaphore/me/plans/{id}", id).with(student("p-nocap")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(9));
    }

    @Test
    @DisplayName("a course that is not in the pensum is refused, naming the field")
    void unknownCourseIsRejected() throws Exception {
        String id = createPlan("p-unknown", "Mi plan");

        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", id, "NO-EXISTE")
                        .with(student("p-unknown"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("code"));
    }

    @Test
    @DisplayName("a level outside 1-12 is refused")
    void levelOutOfRangeIsRejected() throws Exception {
        String id = createPlan("p-range", "Mi plan");

        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(student("p-range"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":13}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The rule this whole class is built around. A semáforo that unlocked courses by dragging
     * them would be telling the student something untrue about what they may enrol in.
     */
    @Test
    @DisplayName("planning a course earlier does NOT make it eligible")
    void planningACourseEarlierDoesNotMakeItEligible() throws Exception {
        stubProfile("p-eligible", SEEDED_PROGRAM, "506232799", 1);

        List<String> before = eligibleCodes("p-eligible");
        String locked = lockedCourse("p-eligible", before);

        String id = createPlan("p-eligible", "Mi plan");
        place("p-eligible", id, locked, 1);

        List<String> after = eligibleCodes("p-eligible");
        assertThat(after)
                .as("moving %s to level 1 must not unlock it - eligibility comes from approved "
                        + "prerequisites, never from a plan", locked)
                .doesNotContain(locked)
                .containsExactlyElementsOf(before);
    }

    // ── Resetting ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetting a course returns it to the level the pensum gives it")
    void resetRemovesThePlacement() throws Exception {
        String id = createPlan("p-reset", "Mi plan");
        place("p-reset", id, FIRST_LEVEL_COURSE, 5);

        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(student("p-reset")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(0));
    }

    @Test
    @DisplayName("resetting a course that was never moved is 404, not a silent success")
    void resetOfUnmovedCourseIs404() throws Exception {
        String id = createPlan("p-reset-404", "Mi plan");

        mockMvc.perform(delete("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(student("p-reset-404")))
                .andExpect(status().isNotFound());
    }

    // ── Authorization ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listing plans without a token is 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a professor has no plans: they have no academic record of their own")
    void professorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(jwtWithRole("prof", "ROLE_PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin has no plans either; they read /api/semaphore/{userId} instead")
    void adminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(jwtWithRole("adm", "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a guest is refused")
    void guestIsForbidden() throws Exception {
        mockMvc.perform(get("/api/semaphore/me/plans").with(jwtWithRole("guest", "ROLE_GUEST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a professor cannot create a plan either")
    void professorCannotCreate() throws Exception {
        mockMvc.perform(post("/api/semaphore/me/plans").with(jwtWithRole("prof2", "ROLE_PROFESSOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("No", SEEDED_PENSUM)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a professor cannot move a course in anybody's plan")
    void professorCannotPlace() throws Exception {
        String id = createPlan("p-prof-target", "Mi plan");

        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", id, FIRST_LEVEL_COURSE)
                        .with(jwtWithRole("prof3", "ROLE_PROFESSOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":3}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private String planJson(String name, String pensumCode) {
        return "{\"name\":\"%s\",\"pensumCode\":\"%s\"}".formatted(name, pensumCode);
    }

    private String createPlan(String subject, String name) throws Exception {
        String body = mockMvc.perform(post("/api/semaphore/me/plans").with(student(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson(name, SEEDED_PENSUM)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private void promote(String subject, String planId) throws Exception {
        mockMvc.perform(patch("/api/semaphore/me/plans/{id}", planId).with(student(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primary\":true}"))
                .andExpect(status().isOk());
    }

    private void place(String subject, String planId, String code, int level) throws Exception {
        mockMvc.perform(put("/api/semaphore/me/plans/{id}/placements/{code}", planId, code)
                        .with(student(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedLevel\":%d}".formatted(level)))
                .andExpect(status().isOk());
    }

    private List<String> allPensumCodes(String subject) throws Exception {
        String body = mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .with(student(subject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return codesOf(mapper.readTree(body));
    }

    private List<String> eligibleCodes(String subject) throws Exception {
        String body = mockMvc.perform(get("/api/semaphore/me/eligible").with(student(subject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return codesOf(mapper.readTree(body));
    }

    /** A course of the seeded pensum that is NOT currently eligible, so it can be shown to stay so. */
    private String lockedCourse(String subject, List<String> eligible) throws Exception {
        return allPensumCodes(subject).stream()
                .filter(code -> !eligible.contains(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the seeded pensum has no locked course, so this test proves nothing"));
    }

    private static List<String> codesOf(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.hasNonNull("code") ? n.get("code").asText()
                        : n.get("pensumItemCode").asText())
                .toList();
    }

    private void stubProfile(String subject, String programCode, String studentCode, int currentLevel) {
        when(userProfileClient.getMyProfile()).thenReturn(new UserProfileResponse(
                subject, "ROLE_STUDENT",
                new UserProfileResponse.Academic(studentCode, programCode, currentLevel)));
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
