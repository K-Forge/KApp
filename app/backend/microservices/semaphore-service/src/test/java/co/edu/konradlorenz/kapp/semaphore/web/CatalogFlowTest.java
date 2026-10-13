package co.edu.konradlorenz.kapp.semaphore.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalog as semaphore 2.0 reads it: the pensum listing, the shape of an item - addressed by
 * {@code pensumItemCode}, shown by {@code sinuCode}, with no printed {@code code} - the filters on
 * the item listing, and the elective bank of a semester.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

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


    // ── Items, as 2.0 shows them ───────────────────────────────────────────────────

    @Test
    @DisplayName("an item is addressed by pensumItemCode and shown by sinuCode, with no printed code")
    void itemShape() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("level", "1")
                        .with(student("shape-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pensumItemCode").exists())
                .andExpect(jsonPath("$[0].sinuCode").exists())
                .andExpect(jsonPath("$[0].code").doesNotExist());
    }

    @Test
    @DisplayName("Estadística Descriptiva keeps its printed item code and shows SINU's, 17080")
    void estadisticaDescriptivaShowsSinusCode() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("level", "6")
                        .with(student("estadistica-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pensumItemCode=='17018')].sinuCode").value(
                        org.hamcrest.Matchers.contains("17080")));
    }

    @Test
    @DisplayName("a prerequisite names an item by its pensumItemCode")
    void prerequisitesAreItemCodes() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/courses", SEEDED_PENSUM)
                        .param("level", "7")
                        .with(student("prereq-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pensumItemCode=='17070')].prerequisites[0]").value(
                        org.hamcrest.Matchers.contains("17018")));
    }

    // ── The elective bank ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the elective bank of the current period lists the courses SINU offers for the slots")
    void electiveBankOfTheCurrentPeriod() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM)
                        .with(student("bank-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].sinuCode").value("59211"))
                .andExpect(jsonPath("$[0].credits").value(3))
                .andExpect(jsonPath("$[0].weeklyHours").value(3))
                .andExpect(jsonPath("$[0].slots.length()").value(0))
                .andExpect(jsonPath("$[2].slots[0]").value("59096"));
    }

    @Test
    @DisplayName("a period SINU has not published answers an empty bank, not an error")
    void unpublishedPeriodIsEmpty() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM)
                        .param("period", "20301")
                        .with(student("bank-future")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the bank of a pensum that does not exist is 404, and a malformed period 400")
    void bankErrors() throws Exception {
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", "NO-EXISTE").with(student("bank-404")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/catalog/pensums/{code}/electives", SEEDED_PENSUM)
                        .param("period", "2026-2").with(student("bank-400")))
                .andExpect(status().isBadRequest());
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
