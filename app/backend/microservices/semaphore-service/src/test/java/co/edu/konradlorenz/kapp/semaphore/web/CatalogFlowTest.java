package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog behaviours that are specific to the admin write path and not already covered
 * by {@code PensumValidatorTest} (the cross-field rules in isolation) or
 * {@code AuthorizationMatrixTest} (that ADMIN can reach these endpoints at all):
 * creating a pensum that already exists, replacing one whose body disagrees with the
 * path, and the level/area/elective filters on the item listing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class CatalogFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private static final String SEEDED_PENSUM = "1015";

    // ── Listing pensums ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the pensum listing carries what a picker needs and not the courses")
    void listingIsASummary() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums").with(admin("list-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pensumCode=='" + SEEDED_PENSUM + "')]").isNotEmpty())
                .andExpect(jsonPath("$[0].programName").exists())
                .andExpect(jsonPath("$[0].status").exists())
                // The count, not the courses: twenty-four pensums of sixty courses is fifteen
                // hundred objects a dropdown has no use for.
                .andExpect(jsonPath("$[0].courses").isNumber());
    }

    @Test
    @DisplayName("creating a pensum whose pensumCode already exists is rejected with 409")
    void creatingADuplicatePensumCodeIsConflict() throws Exception {
        String pensumCode = "DUP-TEST";
        mockMvc.perform(post("/api/catalog/pensums").with(admin("dup-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson(pensumCode)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/catalog/pensums").with(admin("dup-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson(pensumCode)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("replacing a pensum whose body pensumCode disagrees with the path is rejected with 400")
    void replaceWithMismatchedPensumCodeIsRejected() throws Exception {
        mockMvc.perform(put("/api/catalog/pensums/{code}", "PATH-CODE").with(admin("mismatch-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson("BODY-CODE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("pensumCode"));
    }

    @Test
    @DisplayName("replacing a pensum that does not exist yet is rejected with 404")
    void replaceOfUnknownPensumIs404() throws Exception {
        mockMvc.perform(put("/api/catalog/pensums/{code}", "NEVER-CREATED").with(admin("replace-404"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson("NEVER-CREATED")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("listPensumCourses filters by level")
    void listPensumCoursesFiltersByLevel() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("level", "1")
                        .with(student("filter-level-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.hasEntry(org.hamcrest.Matchers.is("level"),
                                org.hamcrest.Matchers.is(1)))));
    }

    @Test
    @DisplayName("listPensumCourses filters by elective slot flag")
    void listPensumCoursesFiltersByElectiveFlag() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("isElectiveSlot", "true")
                        .with(student("filter-elective-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6)) // Electiva I..VI of the printed 1015
                .andExpect(jsonPath("$[0].isElectiveSlot").value(true));
    }

    @Test
    @DisplayName("listPensumCourses filters by area")
    void listPensumCoursesFiltersByArea() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("area", "SI")
                        .with(student("filter-area-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].area").value("SI"));
    }

    private String minimalPensumJson(String pensumCode) throws Exception {
        PensumAreaDto area = new PensumAreaDto("CB", "Ciencias Basicas", "#539392", 3, 4);
        PensumCourseDto course = new PensumCourseDto(
                "M1", "M1", "Minimal Course", 1, 3, 4, null, "CB", false, List.of(), null);
        PensumDto dto = new PensumDto(pensumCode, "506", "Test Program",
                "Test Faculty", "Test Reform", PensumStatus.ACTIVE, 3, 4, 1,
                List.of(area), List.of(course));
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
