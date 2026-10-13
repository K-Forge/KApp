package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Turns an account's ability to sign in on and off.
 *
 * <p>This exists because "deactivate" in the admin portal used to do nothing that mattered. It
 * flipped {@code active} on the profile in user-service, which decides how a row is drawn in a
 * listing and nothing else: the credential here stayed {@code ACTIVE}, sign-in only ever
 * refuses {@code SUSPENDED}, and no code anywhere set {@code SUSPENDED}. The button said it
 * took access away and the person kept signing in.
 *
 * <p>Sign-in is decided in this service, so the switch that stops it has to reach this service.
 * user-service calls it over {@code /internal} when an administrator flips the profile flag, in
 * the same movement, so the two cannot drift apart.
 *
 * <p><strong>An access token already issued stays valid until it expires.</strong> That is
 * inherent to stateless JWTs and it is why the access token's lifetime is an hour rather than a
 * day. What suspending does take back is every refresh token of the account, so no session
 * renews: the apps are signed out at their next renewal, within that hour.
 */
@Service
public class CredentialStatusService {

    private static final Logger log = LoggerFactory.getLogger(CredentialStatusService.class);

    private final CredentialRepository credentials;
    private final SessionService sessions;

    public CredentialStatusService(CredentialRepository credentials, SessionService sessions) {
        this.credentials = credentials;
        this.sessions = sessions;
    }

    /**
     * Idempotent: setting the state the account already holds writes nothing.
     *
     * @throws NotFoundException when no credential carries that {@code userId} - which means the
     *                           two services disagree about who exists, and is worth surfacing
     *                           rather than swallowing.
     */
    public void setSignInAllowed(String userId, boolean allowed) {
        Credential current = credentials.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No credential for user " + userId));

        Credential updated = current.withSignInAllowed(allowed, Instant.now());
        if (updated == current) {
            return;
        }

        credentials.save(updated);
        log.info("Credential for user {} is now {}", userId, updated.status());
        if (!allowed) {
            sessions.revokeAll(userId);
        }
    }
}
