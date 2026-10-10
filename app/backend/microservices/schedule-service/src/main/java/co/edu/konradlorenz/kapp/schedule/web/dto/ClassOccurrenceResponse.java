package co.edu.konradlorenz.kapp.schedule.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * One class actually taking place on one concrete date, already resolved server-side: wire shape of
 * {@code ClassOccurrence}. Built only by {@code mapper.ScheduleMapper#toClassOccurrence}, used
 * identically by the day and the week endpoints.
 *
 * <p>{@code room} is the room in force on that date, and may be null; {@code buildingCode} is null
 * while the sede is not mapped. Both are always written.
 */
public record ClassOccurrenceResponse(
        String sectionCode,
        String sinuCode,
        String pensumItemCode,
        String courseName,
        String group,
        String professor,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        int blocks,
        String sede,
        String buildingCode,
        String room,
        String color
) {
}
