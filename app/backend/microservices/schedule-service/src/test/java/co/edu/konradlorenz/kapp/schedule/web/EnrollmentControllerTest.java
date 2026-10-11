package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.schedule.AbstractScheduleServiceTest;
import co.edu.konradlorenz.kapp.schedule.domain.Enrollment;
import co.edu.konradlorenz.kapp.schedule.domain.Schedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/schedule/me/enrollments}: adding, updating and removing courses. */
class EnrollmentControllerTest extends AbstractScheduleServiceTest {

    private static final String STUDENT_ID = "f0000000-0000-0000-0000-000000000001";

    private static final String DAM_ENROLLMENT_BODY = """
            {"courseCode": "59035", "pensumItemCode": "2033", "courseName": "DESARROLLO DE APLICACIONES MOVILES",
             "level": 8, "credits": 3, "totalHours": 64, "group": "51", "subgroup": null,
             "professor": "MARTÍNEZ ACOSTA JORGE ENRIQUE", "campus": "Sede Principal",
             "startDate": "2026-07-31", "endDate": "2026-11-27", "color": "#C9D329",
             "meetings": [{"dayOfWeek": "FRIDAY", "startTime": "18:15", "endTime": "21:15",
                           "periods": [{"from": "2026-07-31", "to": "2026-11-27", "room": "709"}]}]}""";

    @Test
    @DisplayName("adding a course returns it with server-assigned ids")
    void addEnrollment_returnsCreatedEnrollment() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(DAM_ENROLLMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").isNotEmpty())
                .andExpect(jsonPath("$.courseCode").value("59035"))
                .andExpect(jsonPath("$.meetings[0].meetingId").isNotEmpty())
                .andExpect(jsonPath("$.meetings[0].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.meetings[0].periods[0].room").value("709"));
    }

    @Test
    @DisplayName("adding a course with no active schedule is a 404")
    void addEnrollment_noActiveSchedule_returns404() throws Exception {
        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(DAM_ENROLLMENT_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a meeting conflicting with one already in the schedule is a 409 naming the collision")
    void addEnrollment_conflictingMeeting_returns409() throws Exception {
        Schedule seeded = schedule(STUDENT_ID, "20262", true,
                List.of(estadistica(List.of(meeting(DayOfWeek.MONDAY, "18:15", "20:30",
                        period("2026-08-24", "2026-09-14", "302"))))));
        save(seeded);
        String conflictingEnrollmentId = seeded.enrollments().get(0).enrollmentId();
        String conflictingMeetingId = seeded.enrollments().get(0).meetings().get(0).meetingId();

        String overlappingBody = """
                {"courseCode": "17081", "pensumItemCode": "2019", "courseName": "OTRA MATERIA",
                 "level": 6, "credits": 3, "totalHours": 48, "group": "51", "subgroup": null,
                 "professor": "OTRO PROFESOR", "campus": "Sede Principal",
                 "startDate": "2026-08-31", "endDate": "2026-09-07", "color": "#123456",
                 "meetings": [{"dayOfWeek": "MONDAY", "startTime": "19:00", "endTime": "21:00",
                               "periods": [{"from": "2026-08-31", "to": "2026-09-07", "room": "708"}]}]}""";

        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(overlappingBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].field").value("meetings[0]"))
                .andExpect(jsonPath("$.details[0].issue").value(
                        org.hamcrest.Matchers.containsString(conflictingEnrollmentId)))
                .andExpect(jsonPath("$.details[0].issue").value(
                        org.hamcrest.Matchers.containsString(conflictingMeetingId)));
    }

    @Test
    @DisplayName("two meetings in the same request that conflict with each other are a 409")
    void addEnrollment_meetingsConflictingWithEachOther_returns409() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        String selfConflictingBody = """
                {"courseCode": "17081", "pensumItemCode": "2019", "courseName": "OTRA MATERIA",
                 "level": 6, "credits": 3, "totalHours": 48, "group": "51", "subgroup": null,
                 "professor": "OTRO PROFESOR", "campus": "Sede Principal",
                 "startDate": "2026-08-31", "endDate": "2026-09-07", "color": "#123456",
                 "meetings": [
                   {"dayOfWeek": "MONDAY", "startTime": "18:00", "endTime": "20:00",
                    "periods": [{"from": "2026-08-31", "to": "2026-09-07", "room": "708"}]},
                   {"dayOfWeek": "MONDAY", "startTime": "19:00", "endTime": "21:00",
                    "periods": [{"from": "2026-08-31", "to": "2026-09-07", "room": "302"}]}
                 ]}""";

        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(selfConflictingBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an end time not later than the start time is a 400")
    void addEnrollment_endTimeBeforeStartTime_returns400() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        String badBody = """
                {"courseCode": "17081", "pensumItemCode": "2019", "courseName": "OTRA MATERIA",
                 "level": 6, "credits": 3, "totalHours": 48, "group": "51", "subgroup": null,
                 "professor": "OTRO PROFESOR", "campus": "Sede Principal",
                 "startDate": "2026-08-31", "endDate": "2026-09-07", "color": "#123456",
                 "meetings": [{"dayOfWeek": "MONDAY", "startTime": "20:00", "endTime": "18:00",
                               "periods": [{"from": "2026-08-31", "to": "2026-09-07", "room": "708"}]}]}""";

        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a period date that does not fall on the meeting's day of week is a 400")
    void addEnrollment_periodOffTheMeetingsWeekday_returns400() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        // 2026-08-31 is a Monday; the meeting claims MONDAY but the range starts on a
        // Tuesday-shifted date instead.
        String badBody = """
                {"courseCode": "17081", "pensumItemCode": "2019", "courseName": "OTRA MATERIA",
                 "level": 6, "credits": 3, "totalHours": 48, "group": "51", "subgroup": null,
                 "professor": "OTRO PROFESOR", "campus": "Sede Principal",
                 "startDate": "2026-09-01", "endDate": "2026-09-07", "color": "#123456",
                 "meetings": [{"dayOfWeek": "MONDAY", "startTime": "18:00", "endTime": "19:00",
                               "periods": [{"from": "2026-09-01", "to": "2026-09-07", "room": "708"}]}]}""";

        mockMvc.perform(post("/api/schedule/me/enrollments").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(badBody))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /me/enrollments/{enrollmentId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a field left out of the patch body is not touched")
    void updateEnrollment_omittedField_isUnchanged() throws Exception {
        Schedule seeded = save(schedule(STUDENT_ID, "20262", true, List.of(estadistica(List.of()))));
        String enrollmentId = seeded.enrollments().get(0).enrollmentId();

        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", enrollmentId).with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#000000"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#000000"))
                .andExpect(jsonPath("$.courseName").value("ESTADISTICA DESCRIPTIVA"));
    }

    @Test
    @DisplayName("an explicit null clears the subgroup")
    void updateEnrollment_explicitNullSubgroup_clearsIt() throws Exception {
        Enrollment withSubgroup = new Enrollment("enrollment-1", "17080", "2018", "ESTADISTICA DESCRIPTIVA",
                6, 3, 48, "51", "02", "GÓMEZ RESTREPO LAURA MARCELA", "Sede Principal",
                java.time.LocalDate.parse("2026-07-27"), java.time.LocalDate.parse("2026-11-30"),
                "#539392", List.of());
        save(schedule(STUDENT_ID, "20262", true, List.of(withSubgroup)));

        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", "enrollment-1").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"subgroup": null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subgroup").value(nullValue()));
    }

    @Test
    @DisplayName("sending meetings on the enrollment patch is rejected with 400")
    void updateEnrollment_rejectsMeetingsKey() throws Exception {
        Schedule seeded = save(schedule(STUDENT_ID, "20262", true, List.of(estadistica(List.of()))));
        String enrollmentId = seeded.enrollments().get(0).enrollmentId();

        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", enrollmentId).with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"meetings": []}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("meetings"));
    }

    @Test
    @DisplayName("an empty patch body is rejected with 400")
    void updateEnrollment_emptyBody_returns400() throws Exception {
        Schedule seeded = save(schedule(STUDENT_ID, "20262", true, List.of(estadistica(List.of()))));
        String enrollmentId = seeded.enrollments().get(0).enrollmentId();

        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", enrollmentId).with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("patching an enrollment that does not exist is a 404")
    void updateEnrollment_unknownEnrollment_returns404() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        mockMvc.perform(patch("/api/schedule/me/enrollments/{id}", "does-not-exist").with(student(STUDENT_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"color": "#000000"}"""))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------
    // DELETE /me/enrollments/{enrollmentId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("deleting an enrollment removes it and its meetings")
    void deleteEnrollment_removesIt() throws Exception {
        Schedule seeded = save(schedule(STUDENT_ID, "20262", true, List.of(estadistica(List.of(
                meeting(DayOfWeek.MONDAY, "18:15", "20:30", period("2026-07-27", "2026-11-30", "302")))))));
        String enrollmentId = seeded.enrollments().get(0).enrollmentId();

        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", enrollmentId).with(student(STUDENT_ID)))
                .andExpect(status().isNoContent());

        assertThat(reload(seeded.id()).enrollments()).isEmpty();
    }

    @Test
    @DisplayName("deleting an enrollment that does not exist is a 404")
    void deleteEnrollment_unknownEnrollment_returns404() throws Exception {
        save(schedule(STUDENT_ID, "20262", true, List.of()));

        mockMvc.perform(delete("/api/schedule/me/enrollments/{id}", "does-not-exist").with(student(STUDENT_ID)))
                .andExpect(status().isNotFound());
    }
}
