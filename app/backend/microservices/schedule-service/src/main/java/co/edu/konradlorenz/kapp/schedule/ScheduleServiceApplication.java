package co.edu.konradlorenz.kapp.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Serves a person's class timetable as SINU has it, and never changes it: a student's, the sections
 * they take; a professor's, the sections they teach. Choosing courses happens in SINU, through the
 * university. See {@code docs/api/schedule.openapi.yaml} and {@code docs/api/sinu/}.
 *
 * <p>Nothing is stored. Each timetable is read through
 * {@link co.edu.konradlorenz.kapp.schedule.sinu.SinuTimetablePort} and kept in memory a few minutes at
 * most. What KApp adds to it - the pensum item of each course, the building of each sede and the
 * colours - comes from semaphore-service, map-service and {@link co.edu.konradlorenz.kapp.schedule.color.CourseColors}.
 *
 * <p>Security is configured by {@code common}'s auto-configuration: no annotation or component scan
 * is required here. {@code @EnableFeignClients} activates the catalogue and map clients, and
 * {@code @EnableCaching} the short caches in front of them.
 */
@SpringBootApplication
@EnableFeignClients
@EnableCaching
public class ScheduleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleServiceApplication.class, args);
    }
}
