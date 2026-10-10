package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.schedule.AbstractScheduleServiceTest;
import co.edu.konradlorenz.kapp.schedule.domain.Enrollment;
import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every role, authenticated or not, against every endpoint this service exposes.
 *
 * <p>One test method per (endpoint, caller) pair and one assertion per test, matching
 * {@code user-service}'s {@code UserAuthorizationMatrixTest}: a failure names the exact
 * combination that broke rather than hiding it inside a loop.
 *
 * <p>The twelve {@code /me} endpoints share one rule - {@code ROLE_STUDENT},
 * {@code ROLE_PROFESSOR} and {@code ROLE_ADMIN} succeed, {@code ROLE_GUEST} and anonymous
 * are refused - and {@code GET /api/schedule/{userId}} is the one exception,
 * {@code ROLE_ADMIN} only, including for a student asking about their own id.
 *
 * <p>One schedule, with one enrollment and one meeting at fixed ids, is seeded per allowed
 * role before every test, so a "succeeds" assertion is a genuine 2xx/204 rather than a
 * 404 that happens to also prove the request cleared authorization.
 */
class ScheduleAuthorizationMatrixTest extends AbstractScheduleServiceTest {

    private static final String GUEST_ID = "c0000000-0000-0000-0000-000000000001";
    private static final String STUDENT_ID = "c0000000-0000-0000-0000-000000000002";
    private static final String PROFESSOR_ID = "c0000000-0000-0000-0000-000000000003";
    private static final String ADMIN_ID = "c0000000-0000-0000-0000-000000000004";

    private static final String PERIOD = "20262";
    private static final String CREATE_PERIOD = "20261";
    private static final String ENROLLMENT_ID = "d1111111-1111-1111-1111-111111111111";
    private static final String MEETING_ID = "d2222222-2222-2222-2222-222222222222";

    private static final String TRIVIAL_SCHEDULE_BODY = """
            {"period": "%s", "programCode": "506", "pensumCode": "1015", "level": 8}""";
    private static final String TRIVIAL_ENROLLMENT_BODY = """
            {"courseCode": "48022", "pensumItemCode": "2027", "courseName": "INGENIERIA DE SOFTWARE II",
             "level": 7, "credits": 3, "totalHours": 64, "group": "51", "subgroup": null,
             "professor": "RAMÍREZ SALAZAR DIANA PATRICIA", "campus": "Sede Principal",
             "startDate": "2026-07-29", "endDate": "2026-11-25", "color": "#D51A65",
             "meetings": [{"dayOfWeek": "WEDNESDAY", "startTime": "18:15", "endTime": "21:15",
                           "periods": [{"from": "2026-07-29", "to": "2026-11-25", "room": "710"}]}]}""";
    private static final String TRIVIAL_MEETING_BODY = """
            {"dayOfWeek": "WEDNESDAY", "startTime": "10:00", "endTime": "11:00",
             "periods": [{"from": "2026-07-29", "to": "2026-11-25", "room": "612"}]}""";

    @BeforeEach
    void seedOneScheduleWithOneEnrollmentPerRole() {
        save(scheduleFor(STUDENT_ID));
        save(scheduleFor(PROFESSOR_ID));
        save(scheduleFor(ADMIN_ID));
    }

    private static Schedule scheduleFor(String userId) {
        MeetingPeriod meetingPeriod = new MeetingPeriod(
                LocalDate.parse("2026-07-27"), LocalDate.parse("2026-11-30"), "302");
        Meeting meeting = new Meeting(MEETING_ID, DayOfWeek.MONDAY,
                LocalTime.parse("18:15"), LocalTime.parse("20:30"), List.of(meetingPeriod));
        Enrollment enrollment = new Enrollment(ENROLLMENT_ID, "17080", "2018",
                "ESTADISTICA DESCRIPTIVA", 6, 3, 48, "51", null,
                "GÓMEZ RESTREPO LAURA MARCELA", "Sede Principal",
                LocalDate.parse("2026-07-27"), LocalDate.parse("2026-11-30"), "#539392",
                List.of(meeting));
        return schedule(userId, PERIOD, true, List.of(enrollment));
    }

    // ---------------------------------------------------------------------------------
    // GET /api/schedule/me
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schedule/me: a student reaches their own schedule")
    void getMe_student_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me: a professor reaches their own schedule")
    void getMe_professor_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me: an admin reaches their own schedule")
    void getMe_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(admin(ADMIN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me: a guest is refused")
    void getMe_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/me: an anonymous caller is rejected")
    void getMe_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/schedule/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // POST /api/schedule/me
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/schedule/me: a student may create a schedule")
    void createMe_student_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIVIAL_SCHEDULE_BODY.formatted(CREATE_PERIOD)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me: a professor may create a schedule")
    void createMe_professor_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me").with(professor(PROFESSOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIVIAL_SCHEDULE_BODY.formatted(CREATE_PERIOD)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me: an admin may create a schedule")
    void createMe_admin_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me").with(admin(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIVIAL_SCHEDULE_BODY.formatted(CREATE_PERIOD)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me: a guest is refused")
    void createMe_guest_isForbidden() throws Exception {
        mockMvc.perform(post("/api/schedule/me").with(guest(GUEST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIVIAL_SCHEDULE_BODY.formatted(CREATE_PERIOD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/schedule/me: an anonymous caller is rejected")
    void createMe_anonymous_isRejected() throws Exception {
        mockMvc.perform(post("/api/schedule/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIVIAL_SCHEDULE_BODY.formatted(CREATE_PERIOD)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // DELETE /api/schedule/me
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/schedule/me: a student may delete their own schedule")
    void deleteMe_student_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me").param("period", PERIOD).with(student(STUDENT_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/schedule/me: a professor may delete their own schedule")
    void deleteMe_professor_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me").param("period", PERIOD).with(professor(PROFESSOR_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/schedule/me: an admin may delete their own schedule")
    void deleteMe_admin_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me").param("period", PERIOD).with(admin(ADMIN_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/schedule/me: a guest is refused")
    void deleteMe_guest_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/schedule/me").param("period", PERIOD).with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/schedule/me: an anonymous caller is rejected")
    void deleteMe_anonymous_isRejected() throws Exception {
        mockMvc.perform(delete("/api/schedule/me").param("period", PERIOD))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/schedule/me/periods
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schedule/me/periods: a student may list their periods")
    void listPeriods_student_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(student(STUDENT_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/periods: a professor may list their periods")
    void listPeriods_professor_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/periods: an admin may list their periods")
    void listPeriods_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(admin(ADMIN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/periods: a guest is refused")
    void listPeriods_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/me/periods: an anonymous caller is rejected")
    void listPeriods_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // POST /api/schedule/me/enrollments
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/schedule/me/enrollments: a student may add a course")
    void addEnrollment_student_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_ENROLLMENT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me/enrollments: a professor may add a course")
    void addEnrollment_professor_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments").with(professor(PROFESSOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_ENROLLMENT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me/enrollments: an admin may add a course")
    void addEnrollment_admin_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments").with(admin(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_ENROLLMENT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/schedule/me/enrollments: a guest is refused")
    void addEnrollment_guest_isForbidden() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments").with(guest(GUEST_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_ENROLLMENT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/schedule/me/enrollments: an anonymous caller is rejected")
    void addEnrollment_anonymous_isRejected() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments")
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_ENROLLMENT_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/schedule/me/enrollments/{enrollmentId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH .../enrollments/{id}: a student may update their enrollment")
    void updateEnrollment_student_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#123456"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../enrollments/{id}: a professor may update their enrollment")
    void updateEnrollment_professor_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(professor(PROFESSOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#123456"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../enrollments/{id}: an admin may update their enrollment")
    void updateEnrollment_admin_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(admin(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#123456"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../enrollments/{id}: a guest is refused")
    void updateEnrollment_guest_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(guest(GUEST_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#123456"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH .../enrollments/{id}: an anonymous caller is rejected")
    void updateEnrollment_anonymous_isRejected() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#123456"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // DELETE /api/schedule/me/enrollments/{enrollmentId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE .../enrollments/{id}: a student may remove their enrollment")
    void deleteEnrollment_student_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(student(STUDENT_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../enrollments/{id}: a professor may remove their enrollment")
    void deleteEnrollment_professor_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(professor(PROFESSOR_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../enrollments/{id}: an admin may remove their enrollment")
    void deleteEnrollment_admin_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(admin(ADMIN_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../enrollments/{id}: a guest is refused")
    void deleteEnrollment_guest_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID).with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE .../enrollments/{id}: an anonymous caller is rejected")
    void deleteEnrollment_anonymous_isRejected() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", ENROLLMENT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // POST /api/schedule/me/enrollments/{enrollmentId}/meetings
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("POST .../meetings: a student may add a weekly slot")
    void addMeeting_student_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments/{id}/meetings", ENROLLMENT_ID)
                        .with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_MEETING_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST .../meetings: a professor may add a weekly slot")
    void addMeeting_professor_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments/{id}/meetings", ENROLLMENT_ID)
                        .with(professor(PROFESSOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_MEETING_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST .../meetings: an admin may add a weekly slot")
    void addMeeting_admin_succeeds() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments/{id}/meetings", ENROLLMENT_ID)
                        .with(admin(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_MEETING_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST .../meetings: a guest is refused")
    void addMeeting_guest_isForbidden() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments/{id}/meetings", ENROLLMENT_ID)
                        .with(guest(GUEST_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_MEETING_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST .../meetings: an anonymous caller is rejected")
    void addMeeting_anonymous_isRejected() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments/{id}/meetings", ENROLLMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_MEETING_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/schedule/me/enrollments/{enrollmentId}/meetings/{meetingId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH .../meetings/{id}: a student may update their weekly slot")
    void updateMeeting_student_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endTime": "20:15"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../meetings/{id}: a professor may update their weekly slot")
    void updateMeeting_professor_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(professor(PROFESSOR_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endTime": "20:15"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../meetings/{id}: an admin may update their weekly slot")
    void updateMeeting_admin_succeeds() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(admin(ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endTime": "20:15"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH .../meetings/{id}: a guest is refused")
    void updateMeeting_guest_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(guest(GUEST_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endTime": "20:15"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH .../meetings/{id}: an anonymous caller is rejected")
    void updateMeeting_anonymous_isRejected() throws Exception {
        mockMvc.perform(patch("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endTime": "20:15"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // DELETE /api/schedule/me/enrollments/{enrollmentId}/meetings/{meetingId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE .../meetings/{id}: a student may remove their weekly slot")
    void deleteMeeting_student_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(student(STUDENT_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../meetings/{id}: a professor may remove their weekly slot")
    void deleteMeeting_professor_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(professor(PROFESSOR_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../meetings/{id}: an admin may remove their weekly slot")
    void deleteMeeting_admin_succeeds() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(admin(ADMIN_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE .../meetings/{id}: a guest is refused")
    void deleteMeeting_guest_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID)
                        .with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE .../meetings/{id}: an anonymous caller is rejected")
    void deleteMeeting_anonymous_isRejected() throws Exception {
        mockMvc.perform(delete("/api/schedule/me/enrollments/{eid}/meetings/{mid}", ENROLLMENT_ID, MEETING_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/schedule/me/day
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schedule/me/day: a student may read their day")
    void getMyDay_student_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-08-24").with(student(STUDENT_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/day: a professor may read their day")
    void getMyDay_professor_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-08-24").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/day: an admin may read their day")
    void getMyDay_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-08-24").with(admin(ADMIN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/day: a guest is refused")
    void getMyDay_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-08-24").with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/me/day: an anonymous caller is rejected")
    void getMyDay_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-08-24"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/schedule/me/week
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schedule/me/week: a student may read their week")
    void getMyWeek_student_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-08-24").with(student(STUDENT_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/week: a professor may read their week")
    void getMyWeek_professor_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-08-24").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/week: an admin may read their week")
    void getMyWeek_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-08-24").with(admin(ADMIN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/me/week: a guest is refused")
    void getMyWeek_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-08-24").with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/me/week: an anonymous caller is rejected")
    void getMyWeek_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-08-24"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/schedule/{userId} - ROLE_ADMIN only
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schedule/{userId}: an admin may read another student's schedule")
    void getByUserId_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/schedule/{userId}", STUDENT_ID).with(admin(ADMIN_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/schedule/{userId}: a guest is refused")
    void getByUserId_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/{userId}", STUDENT_ID).with(guest(GUEST_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/{userId}: a student is refused even when the id is their own")
    void getByUserId_studentReachingOwnId_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/{userId}", STUDENT_ID).with(student(STUDENT_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/{userId}: a professor is refused")
    void getByUserId_professor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/{userId}", STUDENT_ID).with(professor(PROFESSOR_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/schedule/{userId}: an anonymous caller is rejected")
    void getByUserId_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/schedule/{userId}", STUDENT_ID))
                .andExpect(status().isUnauthorized());
    }
}
