package co.edu.konradlorenz.kapp.schedule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The date-resolution helper behind both {@code GET /me/day} and {@code GET /me/week},
 * using the exact six-range Estadística Descriptiva example from
 * {@code docs/api/schedule.openapi.yaml}: two ranges in room 302, a gap with no room, a
 * single-day range, and a genuine gap between ranges.
 */
class MeetingResolutionTest {

    private static final Meeting ESTADISTICA = new Meeting(DayOfWeek.MONDAY,
            LocalTime.of(18, 15), LocalTime.of(20, 30), List.of(
                    range("2026-07-27", "2026-08-10", "302"),
                    range("2026-08-24", "2026-09-14", "302"),
                    range("2026-09-21", "2026-09-28", null),
                    range("2026-10-19", "2026-10-26", "302"),
                    range("2026-11-09", "2026-11-09", "302"),
                    range("2026-11-23", "2026-11-30", "302")));

    @Test
    @DisplayName("a date inside a range resolves to that range's room")
    void dateInsideRange_resolvesToRoom() {
        Optional<MeetingPeriod> resolved = MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-08-24"));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().room()).isEqualTo("302");
    }

    @Test
    @DisplayName("a date inside the roomless range resolves with a null room, not absent")
    void dateInsideRoomlessRange_resolvesWithNullRoom() {
        Optional<MeetingPeriod> resolved = MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-09-21"));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().room()).isNull();
    }

    @Test
    @DisplayName("a single-day range resolves on that exact date")
    void singleDayRange_resolvesOnThatDate() {
        Optional<MeetingPeriod> resolved = MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-11-09"));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().room()).isEqualTo("302");
    }

    @Test
    @DisplayName("the first date of a range resolves, inclusive")
    void rangeStart_isInclusive() {
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-07-27"))).isPresent();
    }

    @Test
    @DisplayName("the last date of a range resolves, inclusive")
    void rangeEnd_isInclusive() {
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-08-10"))).isPresent();
    }

    @Test
    @DisplayName("a date in the gap between two ranges resolves to nothing")
    void dateInGap_resolvesToNothing() {
        // 2026-08-17 is the Monday between the 2026-08-10 and 2026-08-24 ranges.
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-08-17"))).isEmpty();
    }

    @Test
    @DisplayName("a date before the meeting's first range resolves to nothing")
    void dateBeforeFirstRange_resolvesToNothing() {
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-07-20"))).isEmpty();
    }

    @Test
    @DisplayName("a date after the meeting's last range resolves to nothing")
    void dateAfterLastRange_resolvesToNothing() {
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-12-07"))).isEmpty();
    }

    @Test
    @DisplayName("a date on the wrong weekday resolves to nothing even inside a range")
    void wrongWeekday_resolvesToNothing() {
        // 2026-08-25 is a Tuesday, inside the 2026-08-24..2026-09-14 range by date alone.
        assertThat(MeetingResolution.periodOn(ESTADISTICA, LocalDate.parse("2026-08-25"))).isEmpty();
    }

    private static MeetingPeriod range(String from, String to, String room) {
        return new MeetingPeriod(LocalDate.parse(from), LocalDate.parse(to), room);
    }
}
