package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.AbstractScheduleServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read a timetable, endpoint by endpoint, as {@code docs/api/schedule.openapi.yaml} says:
 * {@code ROLE_STUDENT} and {@code ROLE_PROFESSOR} read their own; staff, an administrator by
 * themselves and guests take and teach no classes and are refused with 403; no token is 401, never
 * 403. Every case is its own literal assertion, so a failure names the endpoint and the role.
 *
 * <p>And what 2.0 removed stays removed: nothing in this service writes, and no route reads somebody
 * else's timetable.
 */
class ScheduleAuthorizationMatrixTest extends AbstractScheduleServiceTest {

    private static final String OTHER = "ffffffffffffffffffffffff";

    @Test
    @DisplayName("GET /api/schedule/me: student and professor read; staff, admin and guest are refused; anonymous is 401")
    void me() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me").with(professor(PROFESSOR_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me").with(staff(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me").with(admin(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me").with(guest(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/schedule/me/periods: student and professor read; staff, admin and guest are refused; anonymous is 401")
    void periods() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(student(STUDENT_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/periods").with(professor(PROFESSOR_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/periods").with(staff(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/periods").with(admin(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/periods").with(guest(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/periods")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/schedule/me/day: student and professor read; staff, admin and guest are refused; anonymous is 401")
    void day() throws Exception {
        String date = "2026-10-05";
        mockMvc.perform(get("/api/schedule/me/day").param("date", date).with(student(STUDENT_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/day").param("date", date).with(professor(PROFESSOR_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/day").param("date", date).with(staff(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/day").param("date", date).with(admin(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/day").param("date", date).with(guest(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/day").param("date", date)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/schedule/me/week: student and professor read; staff, admin and guest are refused; anonymous is 401")
    void week() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").with(student(STUDENT_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/week").with(professor(PROFESSOR_ID))).andExpect(status().isOk());
        mockMvc.perform(get("/api/schedule/me/week").with(staff(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/week").with(admin(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/week").with(guest(OTHER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedule/me/week")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an administrator who is also a student reads their own timetable as a student")
    void studentWhoIsAlsoAdmin() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(callerWith(STUDENT_ID, KappRoles.STUDENT, KappRoles.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("nothing writes: creating or deleting a timetable is 405, and the enrollment routes are gone")
    void writesAreGone() throws Exception {
        mockMvc.perform(post("/api/schedule/me").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"period\": \"20262\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("This path does not take POST"));
        mockMvc.perform(delete("/api/schedule/me").param("period", "20262").with(student(STUDENT_ID)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("no route reads somebody else's timetable, an administrator's request included")
    void otherPeoplesTimetablesAreGone() throws Exception {
        mockMvc.perform(get("/api/schedule/" + STUDENT_ID).with(admin(OTHER)))
                .andExpect(status().isNotFound());
    }
}
