package co.edu.konradlorenz.kapp.schedule.mapper;

import co.edu.konradlorenz.kapp.schedule.domain.Meeting;
import co.edu.konradlorenz.kapp.schedule.domain.MeetingPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Schedule;
import co.edu.konradlorenz.kapp.schedule.domain.Section;
import co.edu.konradlorenz.kapp.schedule.web.dto.ClassOccurrenceResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.MeetingPeriodResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.MeetingResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.ScheduleResponse;
import co.edu.konradlorenz.kapp.schedule.web.dto.SectionResponse;

/**
 * The single place a domain object turns into the shape the API returns. Every controller and every
 * test goes through here, so the wire format only has to be gotten right once.
 */
public final class ScheduleMapper {

    private ScheduleMapper() {
    }

    public static ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(
                schedule.userId(),
                schedule.period().toString(),
                schedule.programCode(),
                schedule.pensumCode(),
                schedule.level(),
                schedule.active(),
                schedule.source().name(),
                schedule.readAt(),
                schedule.sections().stream().map(ScheduleMapper::toResponse).toList());
    }

    public static SectionResponse toResponse(Section section) {
        return new SectionResponse(
                section.sectionCode(),
                section.sinuCode(),
                section.pensumItemCode(),
                section.courseName(),
                section.level(),
                section.credits(),
                section.totalHours(),
                section.group(),
                section.subgroup(),
                section.professor(),
                section.sede(),
                section.buildingCode(),
                section.startDate(),
                section.endDate(),
                section.color(),
                section.meetings().stream().map(ScheduleMapper::toResponse).toList());
    }

    public static MeetingResponse toResponse(Meeting meeting) {
        return new MeetingResponse(
                meeting.dayOfWeek(),
                meeting.startTime(),
                meeting.endTime(),
                meeting.blocks(),
                meeting.periods().stream().map(ScheduleMapper::toResponse).toList());
    }

    public static MeetingPeriodResponse toResponse(MeetingPeriod period) {
        return new MeetingPeriodResponse(period.from(), period.to(), period.room());
    }

    /**
     * @param resolvedRoom the room in force during the range that matched the requested date - not
     *                     necessarily the meeting's only room, and possibly {@code null}
     */
    public static ClassOccurrenceResponse toClassOccurrence(Section section, Meeting meeting, String resolvedRoom) {
        return new ClassOccurrenceResponse(
                section.sectionCode(),
                section.sinuCode(),
                section.pensumItemCode(),
                section.courseName(),
                section.group(),
                section.professor(),
                meeting.startTime(),
                meeting.endTime(),
                meeting.blocks(),
                section.sede(),
                section.buildingCode(),
                resolvedRoom,
                section.color());
    }
}
