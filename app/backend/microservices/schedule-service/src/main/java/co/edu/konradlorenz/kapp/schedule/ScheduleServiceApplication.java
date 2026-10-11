package co.edu.konradlorenz.kapp.schedule;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Owns each student's per-period class timetable: enrollments, their weekly meetings, and
 * the disjoint date ranges - each with its own room - those meetings are actually taught
 * over. Modelled directly on the university's own SINU report so a future automatic sync
 * needs no migration; see {@code docs/api/schedule.openapi.yaml}.
 *
 * <p>Security is configured by {@code common}'s auto-configuration: no annotation or
 * component scan is required here.
 *
 * <p>{@code @EnableMongock} is NOT optional. Mongock 5.5.1 ships neither
 * {@code AutoConfiguration.imports} nor {@code spring.factories}, so nothing registers
 * it automatically: without this annotation the migrations are silently skipped, indexes
 * are never created and the failure only shows up as duplicate data much later. Every
 * KApp service that talks to MongoDB must carry it.
 *
 * <p>{@code @EnableFeignClients} activates
 * {@link co.edu.konradlorenz.kapp.schedule.catalog.CatalogClient}, which reads the
 * academic catalogue from semaphore-service. {@code @EnableCaching} backs the ~1h cache in
 * front of it - see {@link co.edu.konradlorenz.kapp.schedule.catalog.PensumCatalogService}.
 */
@SpringBootApplication
@EnableMongock
@EnableFeignClients
@EnableCaching
public class ScheduleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleServiceApplication.class, args);
    }
}
