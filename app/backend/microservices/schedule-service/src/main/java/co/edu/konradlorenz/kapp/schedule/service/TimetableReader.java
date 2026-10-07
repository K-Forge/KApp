package co.edu.konradlorenz.kapp.schedule.service;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetable;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetablePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads a timetable from SINU and keeps it in memory for {@code kapp.schedule.timetable-cache}, five
 * minutes by default: the home screen, the day and the week views of one person ask for the same
 * timetable within seconds of each other. Nothing is written anywhere, and nothing outlives the
 * process.
 *
 * <p>Only timetables that exist are kept, so one that appears in SINU is served on the next read.
 */
@Component
public class TimetableReader {

    /** A timetable and when it was read from SINU, which the answer reports as {@code readAt}. */
    public record Reading(SinuTimetable timetable, Instant readAt) {
    }

    private final SinuTimetablePort sinu;
    private final Clock clock;
    private final Cache<String, Reading> readings;

    public TimetableReader(SinuTimetablePort sinu, Clock clock,
                           @Value("${kapp.schedule.timetable-cache}") Duration keepFor) {
        this.sinu = sinu;
        this.clock = clock;
        this.readings = Caffeine.newBuilder().expireAfterWrite(keepFor).maximumSize(10_000).build();
    }

    public Optional<Reading> read(SinuPerson person, AcademicPeriod period) {
        String key = person.userId() + "|" + person.role() + "|" + period;
        Reading kept = readings.getIfPresent(key);
        if (kept != null) {
            return Optional.of(kept);
        }
        Optional<Reading> read = sinu.timetable(person, period).map(t -> new Reading(t, clock.instant()));
        read.ifPresent(reading -> readings.put(key, reading));
        return read;
    }
}
