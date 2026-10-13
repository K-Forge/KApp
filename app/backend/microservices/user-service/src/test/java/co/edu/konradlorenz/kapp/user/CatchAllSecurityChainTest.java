package co.edu.konradlorenz.kapp.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for a security hole that an ordinary-looking edit used to open.
 *
 * <p>The shared chain in {@code common} was declared {@code @ConditionalOnMissingBean}.
 * A service that added its own chain for one narrow purpose - say {@code /internal/**},
 * which is authenticated by a shared secret rather than a token - silently switched the
 * shared chain off, leaving every other route in that service unauthenticated. Nothing
 * failed, no test broke, and the application started perfectly.
 *
 * <p>The chain is now an ordered catch-all instead. This test declares exactly the kind
 * of narrow chain that used to cause the problem, then checks that an unrelated route is
 * still protected. If someone reinstates the condition, this fails.
 *
 * <p>The synthetic chain below matches a path of its own ({@code /test-scoped/**}) rather
 * than the real {@code /internal/**} that {@code InternalApiSecurityConfig} now claims in
 * production: Spring Security refuses to start a context in which two chains declare the
 * identical {@code securityMatcher} ({@code UnreachableFilterChainException}), and this
 * test needs to stay a generic guard on the mechanism itself, independent of whichever
 * concrete narrow chains the service happens to add over time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // The Feign client to auth-service is built at startup and refuses a blank secret, so
        // even a test that never deactivates anybody needs one for the context to come up.
        "kapp.internal.token=test-internal-token-8f2c1d"
})
class CatchAllSecurityChainTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    /**
     * A narrow chain of the sort a service legitimately adds: scoped by
     * {@code securityMatcher} to one path prefix, ordered ahead of the catch-all.
     */
    @TestConfiguration
    static class NarrowChainConfig {

        @Bean
        @Order(10)
        SecurityFilterChain narrowChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/test-scoped/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @Test
    @DisplayName("a narrow chain does not disarm the catch-all on unrelated routes")
    void narrowChainDoesNotDisarmTheCatchAll() throws Exception {
        mockMvc.perform(get("/api/users/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("the catch-all still leaves the health probe open")
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the narrow chain governs the path it claims")
    void narrowChainGovernsItsOwnPath() throws Exception {
        // Permitted by the narrow chain, so it reaches the dispatcher and 404s on a
        // missing handler rather than being rejected as unauthenticated.
        mockMvc.perform(get("/test-scoped/nothing-here"))
                .andExpect(status().isNotFound());
    }
}
