package co.edu.konradlorenz.kapp.auth.identity;

import co.edu.konradlorenz.kapp.auth.config.MicrosoftProperties;
import co.edu.konradlorenz.kapp.auth.error.MicrosoftSignInUnavailableException;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity.DirectoryProfile;
import co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks an ID token a client brings back from Microsoft's sign-in, and reads who it is about.
 *
 * <p>Nothing in the token is believed until all of this holds:
 * <ul>
 *   <li>it is signed with RS256 by a key the tenant publishes - an unsigned token, or one signed
 *       with a shared secret, is refused before any key is looked at;</li>
 *   <li>{@code iss} is the tenant's, so a token from any other directory is refused;</li>
 *   <li>{@code aud} is one of KApp's application ids, so a token Microsoft issued to another
 *       application cannot be replayed here;</li>
 *   <li>{@code exp} is present and not past, give or take a minute of clock skew, and {@code nbf},
 *       when present, has come;</li>
 *   <li>it names the person: {@code oid}, and an address.</li>
 * </ul>
 *
 * <p>Every failure gives the caller the same 401, whatever the reason; the log keeps the reason.
 * Microsoft's keys being unreachable is not the caller's fault, and gives a 503 instead.
 */
@Component
public class MicrosoftIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftIdTokenVerifier.class);

    static final String NOT_VERIFIED = "The Microsoft sign-in could not be verified";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final MicrosoftProperties properties;
    private final JwtDecoder decoder;

    @Autowired
    public MicrosoftIdTokenVerifier(MicrosoftProperties properties) {
        this(properties, restWithTimeouts());
    }

    MicrosoftIdTokenVerifier(MicrosoftProperties properties, RestOperations rest) {
        this.properties = properties;
        this.decoder = properties.configured() ? decoder(properties, rest) : null;
        if (decoder == null) {
            log.info("Signing in with Microsoft is not configured: no tenant, issuer, key set or client id");
        }
    }

    /**
     * @throws InvalidCredentialsException          (401) when the token does not hold
     * @throws MicrosoftSignInUnavailableException (503) when Microsoft is not configured here, or its
     *                                              keys could not be fetched
     */
    public MicrosoftIdentity verify(String idToken) {
        if (decoder == null) {
            throw new MicrosoftSignInUnavailableException("Signing in with Microsoft is not configured on this server");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException refused) {
            log.info("Microsoft ID token refused: {}", refused.getMessage());
            throw new InvalidCredentialsException(NOT_VERIFIED);
        } catch (JwtException unreachable) {
            log.warn("Microsoft ID token could not be checked", unreachable);
            throw new MicrosoftSignInUnavailableException(
                    "Microsoft could not be reached to check the sign-in. Try again in a moment", unreachable);
        }

        String objectId = blankToNull(jwt.getClaimAsString("oid"));
        String email = blankToNull(jwt.getClaimAsString("email"));
        if (email == null) {
            email = blankToNull(jwt.getClaimAsString("preferred_username"));
        }
        if (objectId == null || email == null) {
            log.info("Microsoft ID token refused: it carries no {}", objectId == null ? "oid" : "address");
            throw new InvalidCredentialsException(NOT_VERIFIED);
        }

        return new MicrosoftIdentity(objectId, email.toLowerCase(Locale.ROOT),
                blankToNull(jwt.getClaimAsString("given_name")),
                blankToNull(jwt.getClaimAsString("family_name")),
                blankToNull(jwt.getClaimAsString("name")),
                profile(jwt));
    }

    /** The first profile, in {@link DirectoryProfile} order, that the profile claim holds a value of. */
    private Optional<DirectoryProfile> profile(Jwt jwt) {
        Object claim = jwt.getClaims().get(properties.profileClaim());
        Collection<?> raw = claim instanceof Collection<?> many ? many : claim == null ? List.of() : List.of(claim);
        Set<String> values = raw.stream()
                .map(value -> String.valueOf(value).strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (values.stream().anyMatch(properties.studentValues()::contains)) {
            return Optional.of(DirectoryProfile.STUDENT);
        }
        if (values.stream().anyMatch(properties.professorValues()::contains)) {
            return Optional.of(DirectoryProfile.PROFESSOR);
        }
        if (values.stream().anyMatch(properties.staffValues()::contains)) {
            return Optional.of(DirectoryProfile.STAFF);
        }
        return Optional.empty();
    }

    private static JwtDecoder decoder(MicrosoftProperties properties, RestOperations rest) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(rest)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                require(jwt -> jwt.getExpiresAt() != null, "it has no expiry"),
                require(jwt -> properties.issuers().contains(jwt.getClaimAsString(JwtClaimNames.ISS)),
                        "it was issued by another directory"),
                require(jwt -> jwt.getAudience() != null
                                && jwt.getAudience().stream().anyMatch(properties.clientIds()::contains),
                        "it was issued to another application")));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> require(java.util.function.Predicate<Jwt> holds, String otherwise) {
        return jwt -> holds.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", otherwise, null));
    }

    /** Microsoft answers in milliseconds; a hung key fetch must not hold a sign-in for minutes. */
    private static RestOperations restWithTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
