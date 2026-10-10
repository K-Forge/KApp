package co.edu.konradlorenz.kapp.map.config;

import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The one place the map's access rules are written down.
 *
 * <h2>Why this service is different</h2>
 * The campus map is the ONLY KApp service {@code ROLE_GUEST} can read. A guest is someone
 * with no university account who signed up purely to find their way around campus - a
 * first-year at induction week, a parent at a graduation, a candidate arriving for an
 * entrance exam. Every other service answers a guest with 403.
 *
 * <p>That makes the failure mode here asymmetric and quiet. If a read endpoint forgets
 * about guests, nothing breaks for the developers, the QA account or the demo login: they
 * all hold a student or admin token. The feature simply does not work for the only people
 * it was built for, and no test fails. Hence one rule expressed by HTTP method over the
 * whole prefix, rather than an annotation per handler that a new endpoint can be added
 * without - and a test per endpoint per role, in {@code MapAuthorizationMatrixTest}.
 *
 * <h2>The shape of the rule</h2>
 * <pre>
 * GET    /api/map/**  -> GUEST, STUDENT, PROFESSOR, STAFF, ADMIN
 * POST   /api/map/**  -> ADMIN
 * PUT    /api/map/**  -> ADMIN
 * PATCH  /api/map/**  -> ADMIN
 * DELETE /api/map/**  -> ADMIN
 * anything else       -> denied
 * </pre>
 * Both defaults fall the safe way. A read endpoint added tomorrow is readable by guests
 * without anyone remembering to say so, which is the behaviour this service wants; a write
 * endpoint added tomorrow is admin-only for the same reason. A method nobody thought about
 * is refused outright rather than quietly accepted from any authenticated caller.
 *
 * <p>Guests are still authenticated: {@code ROLE_GUEST} is a role in a signed token, not
 * anonymous access. A request with no token gets 401 here exactly as it does everywhere
 * else.
 *
 * <h2>Why the securityMatcher is mandatory</h2>
 * {@code common} contributes an always-present catch-all chain ordered last, so that a
 * service adding a narrow chain can no longer switch the shared one off by accident - the
 * hole described in {@code docs/INTEGRATION-NOTES.md}. The consequence is that a chain
 * declared here MUST carry a {@code securityMatcher} and an {@code @Order} ahead of the
 * catch-all: without a matcher this chain would claim every request, making the catch-all
 * unreachable, and Spring Security refuses to start with an
 * {@code UnreachableFilterChainException} rather than letting it slide. Paths outside
 * {@code /api/map/**} - the health probe, the OpenAPI document - fall through to the
 * catch-all and keep the rules every service shares.
 */
@Configuration
public class MapSecurityConfig {

    private static final String MAP_PATHS = "/api/map/**";

    /**
     * Anyone holding a KApp token may read the map. Listed longhand rather than as
     * "authenticated" so that the guest role is visible in the source: this line is the
     * feature.
     */
    private static final String[] MAP_READERS = {
            KappRoles.Short.GUEST,
            KappRoles.Short.STUDENT,
            KappRoles.Short.PROFESSOR,
            KappRoles.Short.STAFF,
            KappRoles.Short.ADMIN
    };

    @Bean
    @Order(10)
    public SecurityFilterChain mapSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        return http
                .securityMatcher(MAP_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, MAP_PATHS).hasAnyRole(MAP_READERS)
                        .requestMatchers(HttpMethod.HEAD, MAP_PATHS).hasAnyRole(MAP_READERS)
                        .requestMatchers(HttpMethod.POST, MAP_PATHS).hasRole(KappRoles.Short.ADMIN)
                        .requestMatchers(HttpMethod.PUT, MAP_PATHS).hasRole(KappRoles.Short.ADMIN)
                        .requestMatchers(HttpMethod.PATCH, MAP_PATHS).hasRole(KappRoles.Short.ADMIN)
                        .requestMatchers(HttpMethod.DELETE, MAP_PATHS).hasRole(KappRoles.Short.ADMIN)
                        // Reached only by a method none of the rules above named. Denied
                        // rather than merely authenticated, so an unforeseen method can
                        // never be the loosest rule in the file.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
