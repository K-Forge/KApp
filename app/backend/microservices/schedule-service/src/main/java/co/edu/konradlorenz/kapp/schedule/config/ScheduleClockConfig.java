package co.edu.konradlorenz.kapp.schedule.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The university's clock. Which day it is in Bogotá decides the current period, today's classes and
 * the current week, whatever zone the server runs in; tests replace it with a fixed one.
 */
@Configuration
public class ScheduleClockConfig {

    @Bean
    public Clock scheduleClock(@Value("${kapp.schedule.zone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
