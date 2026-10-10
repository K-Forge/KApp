package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.schedule.AbstractScheduleServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The resolved views, {@code GET /api/schedule/me/day} and {@code /week}, against the test SINU's
 * timetable moved onto 2026-2: on each date, only the meetings that actually take place, with the
 * room in force on that date.
 */
class AgendaControllerTest extends AbstractScheduleServiceTest {

    @Test
    @DisplayName("a Monday in a range resolves to that class and its room")
    void day_resolvesRoom() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-10-05").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sectionCode").value("3101"))
                .andExpect(jsonPath("$[0].sinuCode").value("13013"))
                .andExpect(jsonPath("$[0].pensumItemCode").value("13013"))
                .andExpect(jsonPath("$[0].courseName").value("ECUACIONES DIFERENCIALES"))
                .andExpect(jsonPath("$[0].group").value("21"))
                .andExpect(jsonPath("$[0].professor").value("DOCENTE EJEMPLO UNO"))
                .andExpect(jsonPath("$[0].startTime").value("07:00"))
                .andExpect(jsonPath("$[0].endTime").value("08:30"))
                .andExpect(jsonPath("$[0].blocks").value(2))
                .andExpect(jsonPath("$[0].sede").value("Sede Principal"))
                .andExpect(jsonPath("$[0].buildingCode").value("EC"))
                .andExpect(jsonPath("$[0].room").value("402"))
                .andExpect(jsonPath("$[0].color").value("#522567"));
    }

    @Test
    @DisplayName("a day with two classes lists them by start time")
    void day_sortedByStartTime() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-10-07").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startTime").value("07:00"))
                .andExpect(jsonPath("$[1].startTime").value("09:15"))
                .andExpect(jsonPath("$[1].room").value("711"));
    }

    @Test
    @DisplayName("a date in a range with no room resolves with room null, not absent")
    void day_roomlessRange() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-09-24").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sinuCode").value("31614"))
                .andExpect(jsonPath("$[0].room").value(nullValue()));
    }

    @Test
    @DisplayName("a date in the gap between two ranges has no classes")
    void day_gap_isEmpty() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-09-14").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("a professor's day has only the classes they teach")
    void day_professor() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-10-06").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2026-10-09").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sinuCode").value("46012"))
                .andExpect(jsonPath("$[0].blocks").value(4))
                .andExpect(jsonPath("$[0].pensumItemCode").value(nullValue()));
    }

    @Test
    @DisplayName("the day view requires a date")
    void day_withoutDate_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").with(student(STUDENT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a date in a period SINU has no timetable for is 404")
    void day_otherPeriod_isNotFound() throws Exception {
        mockMvc.perform(get("/api/schedule/me/day").param("date", "2027-03-01").with(student(STUDENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No active schedule found"));
    }

    @Test
    @DisplayName("the week snaps to its Monday, has all seven days, and resolves each one")
    void week_snapsAndResolves() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").param("date", "2026-10-08").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-10-05"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-11"))
                .andExpect(jsonPath("$.days.MONDAY", hasSize(1)))
                .andExpect(jsonPath("$.days.TUESDAY[0].room").value("605"))
                .andExpect(jsonPath("$.days.WEDNESDAY", hasSize(2)))
                .andExpect(jsonPath("$.days.THURSDAY[0].room").value("612"))
                .andExpect(jsonPath("$.days.FRIDAY[0].room").value("709"))
                .andExpect(jsonPath("$.days.SATURDAY[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.days.SATURDAY[0].room").value("301"))
                .andExpect(jsonPath("$.days.SUNDAY", hasSize(0)));
    }

    @Test
    @DisplayName("without a date, the week is the current one")
    void week_defaultsToCurrentWeek() throws Exception {
        mockMvc.perform(get("/api/schedule/me/week").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-10-05"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-11"));
    }
}
