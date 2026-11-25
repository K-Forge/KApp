package co.edu.konradlorenz.kapp.gateway.error;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServiceUnavailableHandler} through the running gateway: schedule-service points at a
 * port nothing listens on, and map-service at a server that answers too late.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceUnavailableHandlerTest {

    private static final int CLOSED_PORT = closedPort();
    private static final HttpServer SLOW = slowServer();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("kapp.services.schedule", () -> "http://127.0.0.1:" + CLOSED_PORT);
        registry.add("kapp.services.map", () -> "http://127.0.0.1:" + SLOW.getAddress().getPort());
        registry.add("spring.cloud.gateway.httpclient.response-timeout", () -> "500ms");
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "500ms");
    }

    @AfterAll
    static void stop() {
        SLOW.stop(0);
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15)).build();
    }

    @Test
    @DisplayName("a service that is down is a 503 with the ApiError body, and the body names no address")
    void serviceDown_is503() {
        client().get().uri("/api/schedule/me/week?date=2026-10-20").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").isEqualTo(ServiceUnavailableHandler.UNAVAILABLE)
                .jsonPath("$.path").isEqualTo("/api/schedule/me/week")
                .jsonPath("$.timestamp").isNotEmpty()
                .consumeWith(result -> assertThat(new String(result.getResponseBodyContent()))
                        .doesNotContain("127.0.0.1").doesNotContain(String.valueOf(CLOSED_PORT)));
    }

    @Test
    @DisplayName("a service that answers too late is a 504 with the ApiError body")
    void serviceTooSlow_is504() {
        client().get().uri("/api/map/buildings").exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.message").isEqualTo(ServiceUnavailableHandler.TIMED_OUT);
    }

    @Test
    @DisplayName("only an unreachable or slow service is answered here: anything else goes on to Spring's handler")
    void otherFailures_areNotThisHandlers() {
        assertThat(ServiceUnavailableHandler.statusFor(new IllegalStateException("a bug"))).isNull();
        assertThat(ServiceUnavailableHandler.statusFor(new RuntimeException(new ConnectException("refused"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ServiceUnavailableHandler.statusFor(new UnknownHostException("schedule-service")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ServiceUnavailableHandler.statusFor(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(ServiceUnavailableHandler.statusFor(new ResponseStatusException(HttpStatus.BAD_REQUEST))).isNull();
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer slowServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    Thread.sleep(3_000);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
