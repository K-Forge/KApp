package co.edu.konradlorenz.kapp.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local sign-in - passwords - is off unless {@code kapp.auth.local-sign-in.enabled} is true, which
 * only development, CI and the mocks set. Off, {@link LocalSignInGate} answers {@code 404} for every
 * path it owns.
 */
@Configuration
public class LocalSignInConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalSignInConfig.class);

    /** Ahead of Spring Security's chain, so the gate answers before anything is authenticated. */
    static final int GATE_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 10;

    @Bean
    @ConditionalOnProperty(name = "kapp.auth.local-sign-in.enabled", havingValue = "false", matchIfMissing = true)
    public FilterRegistrationBean<LocalSignInGate> localSignInGate(ObjectMapper objectMapper) {
        FilterRegistrationBean<LocalSignInGate> registration =
                new FilterRegistrationBean<>(new LocalSignInGate(objectMapper));
        registration.setOrder(GATE_ORDER);
        registration.addUrlPatterns("/auth/*");
        return registration;
    }

    /** Said once at startup, so a server running with passwords never does so unnoticed. */
    @Bean
    @ConditionalOnProperty(name = "kapp.auth.local-sign-in.enabled", havingValue = "true")
    public ApplicationListener<ApplicationReadyEvent> localSignInWarning() {
        return ready -> log.warn("Local sign-in is ENABLED: passwords, registration and invitation codes "
                + "are open. Development and CI only; production signs in with Microsoft.");
    }
}
