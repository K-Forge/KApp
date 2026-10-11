package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.service.ScheduleService;
import co.edu.konradlorenz.kapp.schedule.web.dto.ClassOccurrenceResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.WeekAgendaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Resolved day and week views: the whole reason the schedule → section → meeting → meeting-period
 * nesting exists. The client asks "what's on this date" and gets an already-resolved answer, room
 * included, instead of walking every meeting's periods itself.
 */
@RestController
@RequestMapping("/api/schedule/me")
@PreAuthorize("hasAnyRole('" + KappRoles.Short.STUDENT + "', '" + KappRoles.Short.PROFESSOR + "')")
@Validated
@Tag(name = "Agenda")
public class AgendaController {

    private final ScheduleService scheduleService;

    public AgendaController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/day")
    @Operation(summary = "Get the caller's classes on one date",
            description = "Sorted by startTime ascending. This is the endpoint the mobile home screen calls.")
    public List<ClassOccurrenceResponse> getMyDay(
            @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return scheduleService.day(Callers.person(), date);
    }

    @GetMapping("/week")
    @Operation(summary = "Get the caller's classes for one week",
            description = "date may be any date inside the wanted week; the server snaps it to that "
                    + "week's Monday. Omitted, the current week is returned.")
    public WeekAgendaResponse getMyWeek(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return scheduleService.week(Callers.person(), date);
    }
}
