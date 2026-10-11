package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.client.UserProfileClient;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.error.ProfileServiceUnavailableException;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * An account an administrator removes: its credential here and its profile in user-service.
 *
 * <p>The credential goes first, the reverse of creating one. A credential whose profile could not
 * be removed yet signs nobody in and can be deleted again; a profile removed under a credential
 * that stayed would leave somebody signing in to nothing. Deleting again is safe either way: an
 * account with no credential left still has its profile removed.
 *
 * <p>Never an administrator's, and never one's own: deactivating takes access away and can be
 * undone, and an administrator removing another is the open question SECURITY-AUDIT.md records
 * as S14.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final CredentialRepository credentials;
    private final UserProfileClient userProfiles;
    private final SessionService sessions;

    public AccountDeletionService(CredentialRepository credentials, UserProfileClient userProfiles,
                                  SessionService sessions) {
        this.credentials = credentials;
        this.userProfiles = userProfiles;
        this.sessions = sessions;
    }

    public void delete(String userId, String requestedBy) {
        if (userId.equals(requestedBy)) {
            throw new BusinessRuleException("You cannot delete your own account");
        }
        Optional<Credential> credential = credentials.findByUserId(userId);
        if (credential.isPresent() && credential.get().roles().contains(KappRoles.ADMIN)) {
            throw new BusinessRuleException("An administrator's account is not deleted from here");
        }
        credential.ifPresent(found -> {
            credentials.delete(found);
            log.info("Account {} deleted by {}", found.email(), requestedBy);
        });
        // Also when the credential was already gone: a deletion sent again finishes the job.
        sessions.deleteAll(userId);
        try {
            userProfiles.delete(userId);
        } catch (RuntimeException e) {
            throw new ProfileServiceUnavailableException(
                    "The account was removed, but not yet its profile. Delete it again in a moment.", e);
        }
    }
}
