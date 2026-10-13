package co.edu.konradlorenz.kapp.auth.config;

import co.edu.konradlorenz.kapp.common.security.KappSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * auth-service is the one service with genuinely public endpoints - login, registration,
 * verification and the JWKS document - so it declares its own chain.
 *
 * <p>The chain claims {@code /auth/**} and the JWKS document through a
 * {@code securityMatcher} and nothing else. Everything outside those prefixes falls
 * through to the catch-all in {@code common} and stays protected. The explicit
 * {@code @Order} is required, since a chain without one ties with the catch-all rather
 * than preceding it.
 *
 * <p>It is still a resource server as well: once signed in, a caller uses the same token
 * here as everywhere else.
 *
 * <h2>Trusting a second issuer later</h2>
 * Today every token this service accepts was minted by this service, so a single
 * {@code jwk-set-uri} is enough. When Entra ID starts issuing tokens as well, KApp has to
 * accept both for as long as the migration runs - its own and the tenant's - and the way
 * to do that is not a second filter chain.
 *
 * <p>Spring Security already models it:
 * {@code JwtIssuerAuthenticationManagerResolver} reads the {@code iss} claim of the
 * incoming token and picks the matching {@code AuthenticationManager}, each with its own
 * {@code JwtDecoder} pointed at a different JWKS. Wiring it is roughly:
 *
 * <pre>
 * var resolver = JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(
 *         kappIssuer, entraIssuer);
 * http.oauth2ResourceServer(oauth -&gt; oauth.authenticationManagerResolver(resolver));
 * </pre>
 *
 * <p>Use {@code fromTrustedIssuers} and never the predicate-free variant: resolving an
 * arbitrary issuer means any OIDC provider on the internet can mint a token KApp accepts.
 *
 * <p>It is not wired now because the second issuer does not exist yet - there is no tenant
 * id and no discovery document - and a resolver with one entry is strictly more machinery
 * than {@code jwk-set-uri} for identical behaviour. What matters is that adding the second
 * issuer is a change to this bean, not a redesign of how the services validate tokens.
 */
@Configuration
public class AuthSecurityConfig {

    /**
     * Endpoints reachable without a token. Kept deliberately short - every entry is a
     * door. The JWKS endpoint has to be here: a service cannot authenticate in order to
     * discover how to authenticate.
     */
    static final String[] PUBLIC_AUTH_PATHS = {
            "/auth/login",
            // The refresh token in the body is the proof, and the access token it renews may
            // already have expired. Signing out takes the same token.
            "/auth/refresh",
            "/auth/logout",
            // A temporary password is replaced before it signs anybody in: the current
            // password in the body is the proof, as at login.
            "/auth/password",
            "/auth/register",
            "/auth/verify",
            "/auth/verify/resend",
            // A visitor has no account and nothing to authenticate with: the code reception
            // read out to them IS the credential. That is why it is single-use, dies after
            // 12 hours, and is rate-limited at the gateway alongside login.
            "/auth/visitor-passes/*/redeem",
            "/auth/health",
            "/.well-known/jwks.json"
    };

    @Bean
    @Order(10)
    public SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        return http
                // Claims only the paths this service actually owns. Without a matcher
                // this chain would swallow every request and make the catch-all in
                // `common` unreachable - which Spring Security rejects outright with
                // UnreachableFilterChainException rather than letting it slide.
                // Everything outside these prefixes falls through to the catch-all and
                // stays protected.
                .securityMatcher("/auth/**", "/.well-known/jwks.json")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_AUTH_PATHS).permitAll()
                        // Anything else under /auth/** still needs a token, so adding an
                        // endpoint there does not accidentally make it public.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * BCrypt at strength 12. The default of 10 dates from much slower hardware; 12 keeps
     * a single verification in the tens of milliseconds while making offline cracking
     * substantially more expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
