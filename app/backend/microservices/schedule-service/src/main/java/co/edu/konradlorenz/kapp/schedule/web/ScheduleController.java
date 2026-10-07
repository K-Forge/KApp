package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.common.academic.ValidAcademicPeriod;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.mapper.ScheduleMapper;
import co.edu.konradlorenz.kapp.schedule.service.ScheduleService;
import co.edu.konradlorenz.kapp.schedule.web.dto.SchedulePeriodSummaryResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's timetable for one period, and the periods there is one for. Read-only: SINU is where
 * courses are chosen.
 *
 * <p>Only {@code ROLE_STUDENT} and {@code ROLE_PROFESSOR} get in: staff, administrators by
 * themselves and guests take and teach no classes. The class-level {@code @PreAuthorize} is the one
 * place that is enforced, so it cannot be forgotten on a future method.
 */
@RestController
@RequestMapping("/api/schedule")
@PreAuthorize("hasAnyRole('" + KappRoles.Short.STUDENT + "', '" + KappRoles.Short.PROFESSOR + "')")
@Validated
@Tag(name = "Schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the caller's schedule for a period",
            description = "As SINU has it. When period is omitted the current period is returned.")
    public ScheduleResponse getMySchedule(
            @RequestParam(required = false) @ValidAcademicPeriod String period) {
        return ScheduleMapper.toResponse(scheduleService.schedule(Callers.person(), period));
    }

    @GetMapping("/me/periods")
    @Operation(summary = "List the periods the caller has a schedule for",
            description = "One entry per period SINU has a timetable for, newest period first.")
    public List<SchedulePeriodSummaryResponse> listMySchedulePeriods() {
        return scheduleService.periods(Callers.person());
    }
}
