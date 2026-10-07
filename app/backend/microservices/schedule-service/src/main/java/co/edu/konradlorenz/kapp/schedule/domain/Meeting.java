package co.edu.konradlorenz.kapp.schedule.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * One weekly day + time slot of a section, together with the disjoint date ranges over which it is
 * actually taught and the room in force during each.
 *
 * @param dayOfWeek the weekday this slot repeats on. {@link DayOfWeek}'s own constants (MONDAY..SUNDAY)
 *                  match the contract's {@code DayOfWeek} enum exactly, so no translation is needed
 * @param startTime when the class starts, inclusive
 * @param endTime   when the class ends, always after {@code startTime}
 * @param periods   the disjoint date ranges this slot is taught over, put in ascending order of
 *                  {@code from} by the constructor
 */
public record Meeting(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        List<MeetingPeriod> periods
) {

    /** SINU schedules in blocks of 45 minutes, always. */
    public static final int BLOCK_MINUTES = 45;

    public Meeting {
        periods = periods == null
                ? List.of()
                : periods.stream().sorted(Comparator.comparing(MeetingPeriod::from)).toList();
    }

    /**
     * How many 45-minute blocks the class lasts: 18:15-21:15 is four. Every SINU meeting is a whole
     * number of them; a part of one would count as a whole, so a class is never shown shorter than it
     * is.
     */
    public int blocks() {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return (int) Math.max(1, (minutes + BLOCK_MINUTES - 1) / BLOCK_MINUTES);
    }
}
