package co.edu.konradlorenz.kapp.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who the login rate limit counts, including behind a proxy: Spring leaves the address the proxy
 * forwarded unresolved, and reading it as a resolved one answered every login with a 500.
 */
class AuthRateLimitFilterTest {

    private static MockServerHttpRequest login(InetSocketAddress from) {
        return MockServerHttpRequest.post("/auth/login").remoteAddress(from).build();
    }

    @Test
    @DisplayName("an address a proxy forwarded, which Spring leaves unresolved, is counted by its text")
    void forwardedAddress() {
        assertThat(AuthRateLimitFilter.clientKey(login(InetSocketAddress.createUnresolved("100.73.35.53", 0))))
                .isEqualTo("100.73.35.53");
        assertThat(AuthRateLimitFilter.clientKey(login(new InetSocketAddress("127.0.0.1", 50000))))
                .isEqualTo("127.0.0.1");
        assertThat(AuthRateLimitFilter.clientKey(MockServerHttpRequest.post("/auth/login")
                .header("X-Forwarded-For", "100.73.35.53, 127.0.0.1").build()))
                .isEqualTo("100.73.35.53");
    }

    @Test
    @DisplayName("behind a proxy the login is let through, then stopped after the attempts allowed")
    void loginsThroughAProxyAreCounted() {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(2);
        InetSocketAddress phone = InetSocketAddress.createUnresolved("100.73.35.53", 0);
        for (int attempt = 1; attempt <= 3; attempt++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(login(phone));
            filter.filter(exchange, e -> Mono.empty()).block();
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(attempt <= 2 ? null : HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
