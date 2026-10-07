package co.edu.konradlorenz.kapp.semaphore;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Serves the academic catalog (programs, pensums, the elective bank) and each student's semáforo:
 * the pensum coloured by the status SINU gives every course. The pensum IS the semáforo at Konrad
 * Lorenz, so the two are one service.
 *
 * <p>Everything academic is read from SINU through
 * {@link co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort} and never stored: the student's
 * program, pensum and level, their record and the elective bank. What this service does store is
 * the catalog's backup - the published plans - and the student's own plans, the one thing a
 * student writes.
 *
 * <p>Security is configured by {@code common}'s auto-configuration: no annotation or
 * component scan is required here.
 *
 * <p>{@code @EnableMongock} is NOT optional. Mongock 5.5.1 ships neither
 * {@code AutoConfiguration.imports} nor {@code spring.factories}, so nothing registers
 * it automatically: without this annotation the migrations are silently skipped, indexes
 * are never created and the failure only shows up as duplicate data much later. Every
 * KApp service that talks to MongoDB must carry it.
 */
@SpringBootApplication
@EnableMongock
public class SemaphoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemaphoreServiceApplication.class, args);
    }
}
