package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecord;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuStudent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads a student's record from SINU and keeps it in memory for {@code kapp.semaphore.record-cache},
 * five minutes by default: the semáforo, its summary and the eligible courses are asked for within
 * seconds of each other. Nothing is written anywhere, and nothing outlives the process.
 *
 * <p>Only students are kept, so one SINU starts to know is served on the next read.
 */
@Component
public class StudentRecordReader {

    /** A student, their record, and when it was read from SINU. */
    public record Reading(SinuStudent student, List<SinuRecord> records, Instant readAt) {
    }

    private final SinuRecordPort sinu;
    private final Clock clock;
    private final Cache<String, Reading> readings;

    public StudentRecordReader(SinuRecordPort sinu, Clock clock,
                               @Value("${kapp.semaphore.record-cache}") Duration keepFor) {
        this.sinu = sinu;
        this.clock = clock;
        this.readings = Caffeine.newBuilder().expireAfterWrite(keepFor).maximumSize(10_000).build();
    }

    public Optional<Reading> read(SinuPerson person) {
        Reading kept = readings.getIfPresent(person.userId());
        if (kept != null) {
            return Optional.of(kept);
        }
        Optional<Reading> read = sinu.student(person)
                .map(student -> new Reading(student, sinu.records(person), clock.instant()));
        read.ifPresent(reading -> readings.put(person.userId(), reading));
        return read;
    }
}
