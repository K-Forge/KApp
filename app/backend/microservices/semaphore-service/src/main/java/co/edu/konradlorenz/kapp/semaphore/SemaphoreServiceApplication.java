package co.edu.konradlorenz.kapp.semaphore;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Owns the academic catalog (programs and pensums) and each student's pensum
 * progress - the semaforo. The pensum IS the semaforo at Konrad Lorenz: the grid a
 * student sees is the pensum coloured by their own progress, so the two are one service.
 *
 * <p>It deliberately does NOT own the student's profile - name, contact details, or the
 * {@code programCode} lazy creation resolves from. Those belong to user-service, reached
 * through the {@code UserProfileClient} Feign client enabled below.
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
@EnableFeignClients
@EnableMongock
public class SemaphoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemaphoreServiceApplication.class, args);
    }
}
