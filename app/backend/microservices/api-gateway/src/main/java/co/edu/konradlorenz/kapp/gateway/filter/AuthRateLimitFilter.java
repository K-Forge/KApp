package co.edu.konradlorenz.kapp.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throttles the credential endpoints, which are the only ones worth brute-forcing.
 *
 * <p>Deliberately in-memory rather than Redis-backed. Spring Cloud Gateway's
 * {@code RequestRateLimiter} only ships a Redis implementation, because it assumes
 * several gateway instances sharing a counter. The MVP runs exactly one instance on one
 * machine, so a local counter is correct and saves a container, roughly 200 MB of RAM
 * and a failure mode on a laptop that is already running Xcode and Android Studio.
 *
 * <p>The limitation is real and bounded: this stops working the moment a second gateway
 * instance exists, because each would count separately. Distributed rate limiting with
 * Redis is recorded as deferred work in {@code docs/PROGRESS.md}. Anyone adding a second
 * instance must revisit this file.
 */
@Component
public class AuthRateLimitFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxAttemptsPerWindow;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${kapp.rate-limit.auth-attempts-per-minute:10}") int max) {
        this.maxAttemptsPerWindow = max;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!isCredentialEndpoint(path)) {
            return chain.filter(exchange);
        }

        String client = clientKey(exchange.getRequest());
        if (overLimit(client)) {
            log.warn("Rate limit exceeded for {} on {}", client, path);
            return tooManyRequests(exchange);
        }
        return chain.filter(exchange);
    }

    private static boolean isCredentialEndpoint(String path) {
        return path.equals("/auth/login")
                // Replacing a password takes the current one, so it is guessed at like login.
                || path.equals("/auth/password")
                || path.equals("/auth/register")
                || path.equals("/auth/verify/resend")
                // A visitor pass code IS the credential - there is no account behind it -
                // so redeeming one is guessed at exactly the way a password is. The codes
                // are eight characters from a 32-letter alphabet, which is far too many to
                // brute-force at ten attempts a minute, and that is the point.
                || isVisitorPassRedemption(path);
    }

    /** {@code /auth/visitor-passes/{code}/redeem}, for any code. */
    private static boolean isVisitorPassRedemption(String path) {
        return path.startsWith("/auth/visitor-passes/") && path.endsWith("/redeem");
    }

    private boolean overLimit(String client) {
        Instant now = Instant.now();
        Counter counter = counters.compute(client, (key, existing) ->
                existing == null || existing.windowStart.plus(WINDOW).isBefore(now)
                        ? new Counter(now)
                        : existing);
        return counter.hits.incrementAndGet() > maxAttemptsPerWindow;
    }

    /**
     * Prefers {@code X-Forwarded-For} because in any real deployment a reverse proxy
     * terminates TLS in front of this, and the socket address would otherwise be the
     * proxy for every caller alike.
     *
     * <p>With {@code forward-headers-strategy: framework} that header never gets here:
     * Spring applies it to the remote address and removes it. The address it leaves is
     * unresolved - a host string with no {@code InetAddress} behind it - so it is read as
     * a string. Reading its {@code InetAddress} answered every request through a proxy
     * with a 500, the login included.
     */
    static String clientKey(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Matches the shared ApiError envelope the services return.
        String body = """
                {"timestamp":"%s","status":429,"error":"Too Many Requests",\
                "message":"Too many attempts. Try again in a minute.","path":"%s"}"""
                .formatted(Instant.now(), exchange.getRequest().getPath().value());
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** Runs before routing, so a throttled request never reaches auth-service. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private record Counter(Instant windowStart, AtomicInteger hits) {
        Counter(Instant windowStart) {
            this(windowStart, new AtomicInteger());
        }
    }
}
