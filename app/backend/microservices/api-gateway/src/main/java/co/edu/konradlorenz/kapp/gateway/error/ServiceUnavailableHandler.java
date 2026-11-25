package co.edu.konradlorenz.kapp.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/**
 * Answers {@code 503} when the service behind a route cannot be reached, and {@code 504} when it
 * does not answer in time, each with the {@code ApiError} body every contract promises (#157).
 *
 * <p>Before, a stopped service came back as Spring's {@code 500} and its default body: a client
 * could not tell "the server has a bug" from "that part of KApp is down for a moment". Now it can
 * show a "try again" state. The message is the same for every route and names no address; the log
 * names the route, where it points and the cause.
 *
 * <p>Anything else is not this handler's: it passes on to Spring's, so a genuine gateway fault is
 * still a {@code 500}. It runs before Spring's own handler, which is {@code @Order(-1)}.
 */
@Component
@Order(-2)
public class ServiceUnavailableHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceUnavailableHandler.class);

    static final String UNAVAILABLE = "This part of KApp is not available right now. Try again in a moment.";
    static final String TIMED_OUT = "This part of KApp took too long to answer. Try again in a moment.";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        HttpStatus status = statusFor(failure);
        ServerHttpResponse response = exchange.getResponse();
        if (status == null || response.isCommitted()) {
            return Mono.error(failure);
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI target = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        log.warn("{} {}: route {} to {} - {}", status.value(), exchange.getRequest().getPath().value(),
                route == null ? "none" : route.getId(), target == null ? "unknown" : target,
                rootCause(failure).toString());

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}"""
                .formatted(Instant.now(), status.value(), status.getReasonPhrase(),
                        status == HttpStatus.GATEWAY_TIMEOUT ? TIMED_OUT : UNAVAILABLE,
                        json(exchange.getRequest().getPath().value()));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * {@code 503} when the connection was never made: refused, an unknown host, or a connect
     * timeout (Netty's is a {@link ConnectException}). {@code 504} for the response timeout, which
     * the gateway reports as a {@code 504} itself. {@code null} for anything else.
     */
    static HttpStatus statusFor(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (cause instanceof ResponseStatusException status
                    && status.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (cause instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String json(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
