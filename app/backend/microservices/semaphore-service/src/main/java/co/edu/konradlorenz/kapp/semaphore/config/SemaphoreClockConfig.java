package co.edu.konradlorenz.kapp.semaphore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The university's clock. Which day it is in Bogotá decides the current period, and with it the
 * elective bank a plan is checked against, whatever zone the server runs in; tests replace it with a
 * fixed one.
 */
@Configuration
public class SemaphoreClockConfig {

    @Bean
    public Clock semaphoreClock(@Value("${kapp.semaphore.zone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
