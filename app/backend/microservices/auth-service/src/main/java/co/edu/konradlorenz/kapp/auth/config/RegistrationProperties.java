package co.edu.konradlorenz.kapp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Registration and e-mail verification settings.
 *
 * <p>Bound from {@code kapp.auth.*}, which {@code application.yml} fills from the
 * environment variables {@code docker-compose.yml} already sets. In particular
 * {@code requireEmailVerification} is {@code kapp.auth.require-email-verification},
 * off by default, because there is no SMTP relay yet and registration must not be
 * blocked waiting for one.
 *
 * @param allowedEmailDomains      the domains {@code POST /auth/register} accepts, comma
 *                                 separated. A list rather than one value because the team's
 *                                 own accounts do not live on the university's domain: the
 *                                 institutional address is what a student registers with, and
 *                                 {@code kforge.dev} is what the six of us use. Keeping the
 *                                 two apart means a development account is recognisable as one
 *                                 at a glance, in a log, and in the user directory.
 * @param requireEmailVerification whether a new account must confirm its address before it
 *                                 can sign in. Off by default because there is no SMTP relay
 *                                 yet and registration is the front door of the product;
 *                                 accounts are then created {@code ACTIVE} while
 *                                 {@code emailVerified} still says {@code false}, because
 *                                 nothing was actually verified. Turn it on the moment mail
 *                                 works.
 * @param verificationLinkBase     the URL the verification e-mail points at; the token is
 *                                 appended as {@code ?token=...}
 * @param verificationTtl          how long a verification token stays valid
 * @param mailFrom                 the From address on verification e-mails
 */
@ConfigurationProperties(prefix = "kapp.auth")
public record RegistrationProperties(
        List<String> allowedEmailDomains,
        boolean requireEmailVerification,
        String verificationLinkBase,
        Duration verificationTtl,
        String mailFrom
) {
    public RegistrationProperties {
        allowedEmailDomains = allowedEmailDomains == null || allowedEmailDomains.isEmpty()
                ? List.of("konradlorenz.edu.co")
                : allowedEmailDomains.stream()
                        .map(d -> d.trim().toLowerCase())
                        .filter(d -> !d.isEmpty())
                        .toList();
        verificationLinkBase = orDefault(verificationLinkBase, "http://localhost:8080/auth/verify");
        verificationTtl = verificationTtl == null ? Duration.ofHours(24) : verificationTtl;
        mailFrom = orDefault(mailFrom, "no-reply@konradlorenz.edu.co");
    }

    /** The account status a freshly registered credential gets. */
    public boolean gatesSignInOnVerification() {
        return requireEmailVerification;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
