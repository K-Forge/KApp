package co.edu.konradlorenz.kapp.auth.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints the access tokens every other KApp service validates.
 *
 * <p>The claim set is the contract the whole system depends on, mirrored by
 * {@code CurrentUser} in the {@code common} module:
 * <ul>
 *   <li>{@code sub} - the stable user id. Deliberately not the e-mail: an e-mail can
 *       change, and using it as the subject was what tied identity to a mutable,
 *       forgeable value in the previous design.</li>
 *   <li>{@code email} - for display and logging only, never for authorisation.</li>
 *   <li>{@code roles} - already prefixed, e.g. {@code ["ROLE_STUDENT"]}, matching what
 *       the shared {@code JwtAuthenticationConverter} expects.</li>
 * </ul>
 *
 * <p>An access token cannot be revoked: it expires after {@link JwtProperties#ttl()}, an hour.
 * What can be revoked is the refresh token that renews it - see {@code SessionService}.
 */
@Service
public class JwtIssuer {

    private final RsaKeyProvider keys;
    private final JwtProperties properties;

    public JwtIssuer(RsaKeyProvider keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    /**
     * @param userId the stable identifier that becomes {@code sub}
     * @param email  the account e-mail
     * @param roles  prefixed role names, e.g. {@code ROLE_STUDENT}
     */
    public IssuedToken issue(String userId, String email, List<String> roles) {
        return issue(userId, email, roles, properties.ttl());
    }

    /**
     * Issues a token with a lifetime other than the configured default.
     *
     * <p>The one caller is the visitor day pass, whose 24 hours are a property of the pass
     * rather than of this service's configuration: a visitor is let in for a day, and
     * shortening the ordinary access-token TTL later must not silently shorten that.
     *
     * <p>{@code email} may be null. A visitor has no account and therefore no address, and
     * inventing one - {@code visitor@kapp.local} or similar - would put a value into logs
     * and audit trails that looks like an account and is not.
     */
    public IssuedToken issue(String userId, String email, List<String> roles, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(properties.issuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .claim("email", email)
                .claim("roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(properties.keyId())
                .type(JOSEObjectType.JWT)
                .build();

        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keys.signingKey().toPrivateKey()));
            return new IssuedToken(jwt.serialize(), ttl.toSeconds(), userId, roles);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the access token", e);
        }
    }

    /**
     * @param accessToken the serialised JWT
     * @param expiresIn   lifetime in seconds
     * @param userId      the subject, echoed so clients need not decode the token
     * @param roles       prefixed role names, echoed for the same reason
     */
    public record IssuedToken(String accessToken, long expiresIn, String userId, List<String> roles) {
        public String tokenType() {
            return "Bearer";
        }
    }
}
