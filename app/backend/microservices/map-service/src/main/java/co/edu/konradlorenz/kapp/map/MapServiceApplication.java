package co.edu.konradlorenz.kapp.map;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns the digital campus map: buildings, their floors and static plan images, and the
 * flat collection of spaces a student searches to find a room.
 *
 * <p>Deliberately not geospatial - no basemap, no GPS, no external map provider. It is the
 * only KApp service {@code ROLE_GUEST} can read, since finding your way around campus is
 * useful before you have a university account.
 *
 * <p>Security is configured by {@code common}'s auto-configuration plus
 * {@code MapSecurityConfig}, which adds the guest-readable rule on top of it: no other
 * annotation or component scan is required here.
 *
 * <p>{@code @EnableMongock} is NOT optional. Mongock 5.5.1 ships neither
 * {@code AutoConfiguration.imports} nor {@code spring.factories}, so nothing registers
 * it automatically: without this annotation the migrations are silently skipped, indexes
 * are never created and the failure only shows up as duplicate data much later. Every
 * KApp service that talks to MongoDB must carry it.
 */
@SpringBootApplication
@EnableMongock
public class MapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapServiceApplication.class, args);
    }
}
