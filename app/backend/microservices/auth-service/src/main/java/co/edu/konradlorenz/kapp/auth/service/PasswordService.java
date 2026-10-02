package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.identity.LocalIdentityProvider;
import co.edu.konradlorenz.kapp.auth.jwt.JwtIssuer;
import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Passwords after an account exists: replacing one, and an administrator issuing a temporary
 * one to somebody who cannot sign in.
 *
 * <p>Replacing needs the current password, checked exactly as sign-in checks it, and ends
 * signed in: it is the second half of a first sign-in with a temporary password, and a person
 * who just chose a password should not be asked to type it again.
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final CredentialRepository credentials;
    private final LocalIdentityProvider localIdentity;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public PasswordService(CredentialRepository credentials, LocalIdentityProvider localIdentity,
                           PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.credentials = credentials;
        this.localIdentity = localIdentity;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /** A temporary password, or a person's own, for a new one of their choosing; signed in with it. */
    public JwtIssuer.IssuedToken change(String email, String currentPassword, String newPassword,
                                        List<String> allowedRoles) {
        Credential credential = localIdentity.verify(email, currentPassword);
        // Before anything changes: a client for one kind of account never sets another's password.
        localIdentity.requireAllowedRole(credential, allowedRoles == null ? List.of() : allowedRoles);
        if (passwordEncoder.matches(newPassword, credential.passwordHash())) {
            throw new BusinessRuleException("The new password must differ from the current one",
                    List.of(new ApiError.FieldIssue("newPassword", "Must differ from the current password")));
        }
        Credential saved = credentials.save(
                credential.withPassword(passwordEncoder.encode(newPassword), Instant.now()));
        log.info("Password replaced for {}{}", saved.email(),
                credential.mustChangePassword() ? ", which had a temporary one" : "");
        return jwtIssuer.issue(saved.userId(), saved.email(), saved.roles());
    }

    /** A new temporary password for an account: whatever it had stops working now. */
    public Issued issueTemporary(String userId) {
        Credential credential = credentials.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        if (credential.provider() != Credential.Provider.LOCAL) {
            throw new BusinessRuleException("This account signs in through the university, not with a password");
        }
        String password = TemporaryPasswords.next();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TemporaryPasswords.LIFETIME);
        Credential saved = credentials.save(
                credential.withTemporaryPassword(passwordEncoder.encode(password), expiresAt, now));
        log.info("Temporary password issued for {}, until {}", saved.email(), expiresAt);
        return new Issued(saved, password, expiresAt);
    }

    /** An account and its temporary password, which exists in plain text only in this answer. */
    public record Issued(Credential credential, String temporaryPassword, Instant expiresAt) {
    }
}
