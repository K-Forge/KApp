package co.edu.konradlorenz.kapp.schedule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingTest {

    private static Meeting meeting(String from, String to) {
        return new Meeting(DayOfWeek.MONDAY, LocalTime.parse(from), LocalTime.parse(to), List.of());
    }

    @Test
    @DisplayName("a meeting lasts whole 45-minute blocks: 18:15-21:15 is four, 07:00-08:30 is two")
    void blocks() {
        assertThat(meeting("18:15", "21:15").blocks()).isEqualTo(4);
        assertThat(meeting("07:00", "08:30").blocks()).isEqualTo(2);
        assertThat(meeting("09:15", "11:30").blocks()).isEqualTo(3);
    }

    @Test
    @DisplayName("part of a block counts as a whole one, so a class is never shown shorter than it is")
    void partialBlock() {
        assertThat(meeting("07:00", "07:30").blocks()).isEqualTo(1);
        assertThat(meeting("07:00", "08:00").blocks()).isEqualTo(2);
    }

    @Test
    @DisplayName("the ranges are kept in order of their first day")
    void rangesInOrder() {
        Meeting meeting = new Meeting(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(8, 30), List.of(
                new MeetingPeriod(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-11-16"), "402"),
                new MeetingPeriod(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-09-07"), "402")));
        assertThat(meeting.periods().get(0).from()).isEqualTo("2026-07-27");
    }
}
