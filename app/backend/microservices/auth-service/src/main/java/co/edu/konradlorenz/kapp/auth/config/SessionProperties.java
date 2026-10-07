package co.edu.konradlorenz.kapp.auth.config;

import co.edu.konradlorenz.kapp.auth.domain.RefreshToken;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long a refresh token lasts when it is not used, by client. Each renewal starts the count
 * again, so a session ends only when it goes unused this long, or is revoked.
 *
 * @param appTtl    the apps: a person who opens KApp at least once a month never signs in again
 * @param portalTtl the admin portal: a working day
 */
@ConfigurationProperties(prefix = "kapp.auth.sessions")
public record SessionProperties(Duration appTtl, Duration portalTtl) {

    public SessionProperties {
        appTtl = appTtl == null ? Duration.ofDays(30) : appTtl;
        portalTtl = portalTtl == null ? Duration.ofHours(12) : portalTtl;
    }

    public Duration ttlFor(RefreshToken.Client client) {
        return switch (client) {
            case APP -> appTtl;
            case PORTAL -> portalTtl;
        };
    }
}
