package co.edu.konradlorenz.kapp.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The complete routing table.
 *
 * <p>Routes are declared explicitly rather than discovered. A discovery locator would
 * auto-expose every service at {@code /{service-id}/**}, including the internal endpoints
 * below, and would quietly grow a new public surface every time somebody adds a service.
 * That was finding S4.
 *
 * <p>Each service is reached at the address in {@code kapp.services.*}: its name, which
 * Docker Compose and Kubernetes resolve and Kubernetes balances across replicas, or
 * {@code 127.0.0.1} and its port on a host-networked server. There is no registry.
 *
 * <p>Note what is absent: there is no route for {@code /internal/**}. Those endpoints -
 * user-service's profile creation, called by auth-service during registration - are
 * reachable only from inside the compose network, and only with the shared internal
 * token. Nothing outside can reach them at all.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               @Value("${kapp.services.auth}") String auth,
                               @Value("${kapp.services.user}") String user,
                               @Value("${kapp.services.semaphore}") String semaphore,
                               @Value("${kapp.services.schedule}") String schedule,
                               @Value("${kapp.services.map}") String map) {
        return builder.routes()

                // Declared FIRST, deliberately. The gateway takes the first matching
                // route, so /auth/** would otherwise swallow /auth/v3/api-docs before the
                // rewrite could strip the prefix - the request would reach auth-service as
                // /auth/v3/api-docs, which its own security chain claims and refuses
                // without a token. A 401 on a documentation URL is a confusing way to
                // discover a route-ordering bug.
                //
                // Aggregated OpenAPI documents, one per service, for the Swagger UI.
                .route("auth-docs", r -> r
                        .path("/auth/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(auth))
                .route("user-docs", r -> r
                        .path("/users/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(user))
                .route("semaphore-docs", r -> r
                        .path("/semaphore/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(semaphore))
                .route("schedule-docs", r -> r
                        .path("/schedule/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(schedule))
                .route("map-docs", r -> r
                        .path("/map/v3/api-docs")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(map))

                // Public: login, registration, verification.
                .route("auth-service", r -> r
                        .path("/auth/**")
                        .uri(auth))

                // Public and required: every other service fetches this to verify tokens.
                .route("auth-jwks", r -> r
                        .path("/.well-known/jwks.json")
                        .uri(auth))

                .route("user-service", r -> r
                        .path("/api/users/**")
                        .uri(user))

                // semaphore-service owns the academic catalogue as well as student
                // progress, because the pensum is what the semaforo displays.
                .route("semaphore-service", r -> r
                        .path("/api/semaphore/**", "/api/catalog/**")
                        .uri(semaphore))

                .route("schedule-service", r -> r
                        .path("/api/schedule/**")
                        .uri(schedule))

                .route("map-service", r -> r
                        .path("/api/map/**")
                        .uri(map))

                .build();
    }
}
