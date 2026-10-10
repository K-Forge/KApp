package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.schedule.AbstractScheduleServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/schedule/me} and {@code GET /api/schedule/me/periods}, against the test SINU: the
 * invented level-5 student of {@code docs/api/sinu/example.json} and the professor who teaches two of
 * their sections, on 5 October 2026.
 */
class ScheduleControllerTest extends AbstractScheduleServiceTest {

    @Test
    @DisplayName("a student gets the current period's timetable, marked as test data")
    void student_getsCurrentTimetable() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(STUDENT_ID))
                .andExpect(jsonPath("$.period").value("20262"))
                .andExpect(jsonPath("$.programCode").value("506"))
                .andExpect(jsonPath("$.pensumCode").value("1015"))
                .andExpect(jsonPath("$.level").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.source").value("TEST"))
                .andExpect(jsonPath("$.readAt").value("2026-10-05T15:00:00Z"))
                .andExpect(jsonPath("$.sections", hasSize(6)));
    }

    @Test
    @DisplayName("a section carries SINU's fields, the meetings with their blocks, and what KApp adds")
    void section_hasSinuFieldsAndKappAdditions() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].sectionCode").value("3101"))
                .andExpect(jsonPath("$.sections[0].sinuCode").value("13013"))
                .andExpect(jsonPath("$.sections[0].pensumItemCode").value("13013"))
                .andExpect(jsonPath("$.sections[0].courseName").value("ECUACIONES DIFERENCIALES"))
                .andExpect(jsonPath("$.sections[0].level").value(5))
                .andExpect(jsonPath("$.sections[0].credits").value(3))
                .andExpect(jsonPath("$.sections[0].totalHours").value(64))
                .andExpect(jsonPath("$.sections[0].group").value("21"))
                .andExpect(jsonPath("$.sections[0].subgroup").value(nullValue()))
                .andExpect(jsonPath("$.sections[0].professor").value("DOCENTE EJEMPLO UNO"))
                .andExpect(jsonPath("$.sections[0].sede").value("Sede Principal"))
                .andExpect(jsonPath("$.sections[0].buildingCode").value("EC"))
                .andExpect(jsonPath("$.sections[0].startDate").value("2026-07-27"))
                .andExpect(jsonPath("$.sections[0].endDate").value("2026-11-18"))
                .andExpect(jsonPath("$.sections[0].color").value("#522567"))
                .andExpect(jsonPath("$.sections[0].meetings", hasSize(2)))
                .andExpect(jsonPath("$.sections[0].meetings[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.sections[0].meetings[0].startTime").value("07:00"))
                .andExpect(jsonPath("$.sections[0].meetings[0].endTime").value("08:30"))
                .andExpect(jsonPath("$.sections[0].meetings[0].blocks").value(2))
                .andExpect(jsonPath("$.sections[0].meetings[0].periods[0].from").value("2026-07-27"))
                .andExpect(jsonPath("$.sections[0].meetings[0].periods[0].to").value("2026-09-07"))
                .andExpect(jsonPath("$.sections[0].meetings[0].periods[0].room").value("402"));
    }

    @Test
    @DisplayName("a subgroup, a range with no room and a room that changes come through as SINU has them")
    void subgroupRoomlessRangeAndRoomChange() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].sectionCode").value("3102"))
                .andExpect(jsonPath("$.sections[1].subgroup").value("02"))
                .andExpect(jsonPath("$.sections[2].meetings[0].periods[0].room").value("710"))
                .andExpect(jsonPath("$.sections[2].meetings[0].periods[1].room").value("711"))
                .andExpect(jsonPath("$.sections[3].meetings[0].periods[1].from").value("2026-09-24"))
                .andExpect(jsonPath("$.sections[3].meetings[0].periods[1].to").value("2026-09-24"))
                .andExpect(jsonPath("$.sections[3].meetings[0].periods[1].room").value(nullValue()));
    }

    @Test
    @DisplayName("each course has a colour of its own, from the six that are not the pink")
    void coursesHaveDistinctColours() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[*].color", containsInAnyOrder(
                        "#539392", "#C9D329", "#522567", "#3E823E", "#B62325", "#592E2A")));
    }

    @Test
    @DisplayName("a course outside the catalogue has no pensum item")
    void courseOutsideCatalogue_hasNullPensumItem() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[5].sinuCode").value("75081"))
                .andExpect(jsonPath("$.sections[5].pensumItemCode").value(nullValue()));
    }

    @Test
    @DisplayName("with semaphore-service and map-service down, the timetable is still served, without their parts")
    void neighboursDown_failOpen() throws Exception {
        when(catalogClient.listPensumCourses("1015")).thenThrow(new RuntimeException("semaphore-service is down"));
        when(mapClient.listBuildings()).thenThrow(new RuntimeException("map-service is down"));

        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections", hasSize(6)))
                .andExpect(jsonPath("$.sections[0].pensumItemCode").value(nullValue()))
                .andExpect(jsonPath("$.sections[0].buildingCode").value(nullValue()));
    }

    @Test
    @DisplayName("a sede nobody has mapped has no building")
    void unmappedSede_hasNullBuilding() throws Exception {
        when(mapClient.listBuildings()).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule/me").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].sede").value("Sede Principal"))
                .andExpect(jsonPath("$.sections[0].buildingCode").value(nullValue()));
    }

    @Test
    @DisplayName("a professor gets the sections they teach, with no program, pensum, level or pensum item")
    void professor_getsTheirSections() throws Exception {
        mockMvc.perform(get("/api/schedule/me").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(PROFESSOR_ID))
                .andExpect(jsonPath("$.programCode").value(nullValue()))
                .andExpect(jsonPath("$.pensumCode").value(nullValue()))
                .andExpect(jsonPath("$.level").value(nullValue()))
                .andExpect(jsonPath("$.sections", hasSize(2)))
                .andExpect(jsonPath("$.sections[*].sectionCode", containsInAnyOrder("3101", "3105")))
                .andExpect(jsonPath("$.sections[0].pensumItemCode").value(nullValue()))
                .andExpect(jsonPath("$.sections[0].buildingCode").value("EC"));
    }

    @Test
    @DisplayName("the current period asked for by name is the same timetable")
    void currentPeriodByName() throws Exception {
        mockMvc.perform(get("/api/schedule/me").param("period", "20262").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("20262"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("a period SINU has no timetable for is 404")
    void otherPeriod_isNotFound() throws Exception {
        mockMvc.perform(get("/api/schedule/me").param("period", "20271").with(student(STUDENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No schedule found for period 20271"));
    }

    @Test
    @DisplayName("a period not written as YYYYS is 400")
    void malformedPeriod_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/schedule/me").param("period", "2026-2").with(student(STUDENT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the period switcher lists the current period with its number of sections")
    void periods_listsCurrent() throws Exception {
        mockMvc.perform(get("/api/schedule/me/periods").with(student(STUDENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].period").value("20262"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].sectionCount").value(6));

        mockMvc.perform(get("/api/schedule/me/periods").with(professor(PROFESSOR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sectionCount").value(2));
    }
}
