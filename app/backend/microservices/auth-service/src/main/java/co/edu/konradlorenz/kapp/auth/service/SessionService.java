package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.config.SessionProperties;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.domain.RefreshToken;
import co.edu.konradlorenz.kapp.auth.error.AccountDeactivatedException;
import co.edu.konradlorenz.kapp.auth.jwt.JwtIssuer;
import co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Sessions: the refresh token handed out with every sign-in, and what renewing, reusing and
 * revoking one does.
 *
 * <p>An access token lasts an hour and cannot be taken back, so everything that has to outlive it
 * - staying signed in for a month, signing out, losing access when an administrator says so -
 * happens here, at renewal.
 *
 * <ul>
 *   <li><strong>Rotation.</strong> Every renewal returns a new refresh token and marks the one sent
 *       as used.</li>
 *   <li><strong>Reuse.</strong> A used token sent again revokes its whole family: two parties hold
 *       the same session, and nothing here can tell the person from whoever copied it.</li>
 *   <li><strong>Sliding expiry.</strong> Each new token lasts its full time from the moment it is
 *       issued, so only a session left unused that long ends on its own.</li>
 *   <li><strong>The account now.</strong> A renewal signs the roles and the status the account has
 *       at that moment, so a permission granted or taken away reaches the client within the
 *       hour, and a deactivated account is refused with 403.</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Marks a KApp refresh token wherever one is pasted or logged by mistake. */
    static final String PREFIX = "rt_";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One answer for every way a refresh token fails, so none can be told apart from outside. */
    static final String SESSION_ENDED = "The session has ended. Sign in again";

    private final MongoTemplate mongo;
    private final CredentialRepository credentials;
    private final JwtIssuer jwtIssuer;
    private final SessionProperties properties;

    public SessionService(MongoTemplate mongo, CredentialRepository credentials, JwtIssuer jwtIssuer,
                          SessionProperties properties) {
        this.mongo = mongo;
        this.credentials = credentials;
        this.jwtIssuer = jwtIssuer;
        this.properties = properties;
    }

    /** A new session for an access token just issued at sign-in: the first token of a new family. */
    public SessionTokens open(JwtIssuer.IssuedToken access, RefreshToken.Client client) {
        Instant now = Instant.now();
        return new SessionTokens(access,
                issueRefresh(UUID.randomUUID().toString(), access.userId(), client, now));
    }

    /**
     * Exchanges a refresh token for a new access token and the next refresh token of its family.
     *
     * @throws InvalidCredentialsException (401) for a token that is unknown, expired, revoked or
     *                                     already used - the last revoking its family
     * @throws AccountDeactivatedException (403) when the account was deactivated
     */
    public SessionTokens renew(String rawToken) {
        Instant now = Instant.now();
        RefreshToken presented = mongo.findOne(byHash(rawToken), RefreshToken.class);
        if (presented == null || presented.expired(now)) {
            throw ended();
        }

        Credential account = credentials.findByUserId(presented.userId()).orElseThrow(SessionService::ended);
        if (!account.canSignIn()) {
            log.info("Renewal refused for user {}: the account is {}", account.userId(), account.status());
            throw new AccountDeactivatedException();
        }
        if (presented.revokedAt() != null) {
            throw ended();
        }

        // Claimed atomically: of two renewals racing with the same token, one gets past this and
        // the other is a reuse like any other.
        RefreshToken claimed = presented.usedAt() != null ? null : mongo.findAndModify(
                Query.query(Criteria.where("id").is(presented.id())
                        .and("usedAt").is(null)
                        .and("revokedAt").is(null)),
                new Update().set("usedAt", now),
                RefreshToken.class);
        if (claimed == null) {
            long revoked = revokeFamily(presented.familyId(), now);
            log.warn("A used refresh token of user {} was presented again: {} tokens of its family revoked",
                    presented.userId(), revoked);
            throw ended();
        }

        IssuedRefresh next = issueRefresh(presented.familyId(), account.userId(), presented.client(), now);
        // The other side of the race: a reuse may have revoked the family between the claim and
        // the insert, and the token just written would escape it. Checked after writing, so the
        // family ends revoked whichever way the two renewals interleave.
        if (mongo.exists(Query.query(Criteria.where("id").is(presented.id()).and("revokedAt").ne(null)),
                RefreshToken.class)) {
            revokeFamily(presented.familyId(), now);
            throw ended();
        }

        JwtIssuer.IssuedToken access = jwtIssuer.issue(account.userId(), account.email(), account.roles());
        return new SessionTokens(access, next);
    }

    /**
     * Ends the session a refresh token belongs to: its whole family is revoked. A token that is
     * already unknown or revoked is not an error, so a client can always sign out.
     */
    public void close(String rawToken) {
        RefreshToken presented = mongo.findOne(byHash(rawToken), RefreshToken.class);
        if (presented != null) {
            revokeFamily(presented.familyId(), Instant.now());
        }
    }

    /** Every session of an account, for a deactivation: its apps sign out at their next renewal. */
    public void revokeAll(String userId) {
        long revoked = mongo.updateMulti(
                Query.query(Criteria.where("userId").is(userId).and("revokedAt").is(null)),
                new Update().set("revokedAt", Instant.now()),
                RefreshToken.class).getModifiedCount();
        log.info("{} refresh tokens of user {} revoked", revoked, userId);
    }

    /** Every session of an account that is being deleted: nothing of it is kept. */
    public void deleteAll(String userId) {
        mongo.remove(Query.query(Criteria.where("userId").is(userId)), RefreshToken.class);
    }

    private long revokeFamily(String familyId, Instant now) {
        return mongo.updateMulti(
                Query.query(Criteria.where("familyId").is(familyId).and("revokedAt").is(null)),
                new Update().set("revokedAt", now),
                RefreshToken.class).getModifiedCount();
    }

    private IssuedRefresh issueRefresh(String familyId, String userId, RefreshToken.Client client, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration ttl = properties.ttlFor(client);
        mongo.insert(new RefreshToken(null, VerificationService.hash(raw), familyId, userId, client, now,
                now.plus(ttl), null, null));
        return new IssuedRefresh(raw, ttl.toSeconds());
    }

    private static Query byHash(String rawToken) {
        return Query.query(Criteria.where("tokenHash").is(VerificationService.hash(rawToken)));
    }

    private static InvalidCredentialsException ended() {
        return new InvalidCredentialsException(SESSION_ENDED);
    }

    /**
     * @param token     the refresh token, shown to the client this once
     * @param expiresIn seconds it lasts if it is not used
     */
    public record IssuedRefresh(String token, long expiresIn) {
    }

    /** What a sign-in or a renewal hands the client: the access token and the next refresh token. */
    public record SessionTokens(JwtIssuer.IssuedToken access, IssuedRefresh refresh) {
    }
}
