package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.client.UserProfileClient;
import co.edu.konradlorenz.kapp.auth.client.UserProfileView;
import co.edu.konradlorenz.kapp.auth.config.RegistrationProperties;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.error.ProfileServiceUnavailableException;
import co.edu.konradlorenz.kapp.auth.web.AccountRequest;
import co.edu.konradlorenz.kapp.auth.web.RegistrationRequest;
import co.edu.konradlorenz.kapp.auth.web.RegistrationResponse;
import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.DuplicateResourceException;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Creates accounts.
 *
 * <h2>Ordering, and why it is not the obvious one</h2>
 * The profile is created in user-service <strong>before</strong> the credential is written
 * here. That looks backwards - the credential is this service's own data - but consider
 * each failure:
 *
 * <ul>
 *   <li>Profile first, credential fails: a profile exists with nobody able to log into it.
 *       The upsert is idempotent on e-mail, so the user simply registers again and the
 *       same profile is reused. A retryable orphan.</li>
 *   <li>Credential first, profile fails: a credential that authenticates into a void. The
 *       user can log in and every screen that needs a profile breaks, and no retry fixes
 *       it because the credential already exists and registration now answers 409.</li>
 * </ul>
 *
 * <h2>409 comes from the index, not from the pre-check</h2>
 * The existence check up front exists to give a fast, friendly answer and to avoid
 * bothering user-service. It is not the guarantee: two simultaneous registrations for the
 * same address both pass it. The unique index on {@code credentials.email} is what
 * actually prevents the duplicate, and its {@link DuplicateKeyException} is translated
 * here.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final CredentialRepository credentials;
    private final UserProfileClient userProfiles;
    private final VerificationService verification;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties properties;

    public RegistrationService(CredentialRepository credentials,
                               UserProfileClient userProfiles,
                               VerificationService verification,
                               PasswordEncoder passwordEncoder,
                               RegistrationProperties properties) {
        this.credentials = credentials;
        this.userProfiles = userProfiles;
        this.verification = verification;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * Password registration, for local development only: the account has no profile role
     * ({@code ROLE_UNASSIGNED}) until an administrator gives it one in the portal, and never a
     * permission.
     */
    public RegistrationResponse register(RegistrationRequest request) {
        String email = normalise(request.email());
        requireAllowedDomain(email);
        requireAvailable(email);

        Credential credential = createAccount(email, request.password(),
                request.firstName(), request.lastName(), KappRoles.UNASSIGNED, initialStatus(), null);
        verification.issueAndSend(credential);
        return respond(credential);
    }

    /**
     * An account an administrator creates, with a temporary password the person replaces at
     * first sign-in. The same rules as registration - the domain, a profile role and never a
     * permission - but the administrator chooses the profile role. It may sign in at once, since
     * somebody vouched for it, but its address is still not verified.
     */
    public PasswordService.Issued createWithTemporaryPassword(AccountRequest request) {
        String email = normalise(request.email());
        requireAllowedDomain(email);
        requireAvailable(email);
        String role = request.profileRole();
        if (!KappRoles.ASSIGNABLE_PROFILE_ROLES.contains(role)) {
            throw new BusinessRuleException("An account is created with a profile role",
                    List.of(new ApiError.FieldIssue("profileRole", "ROLE_STUDENT, ROLE_PROFESSOR or ROLE_STAFF")));
        }
        String password = TemporaryPasswords.next();
        Instant expiresAt = Instant.now().plus(TemporaryPasswords.LIFETIME);
        Credential credential = createAccount(email, password, request.firstName(), request.lastName(),
                role, Credential.Status.ACTIVE, expiresAt);
        return new PasswordService.Issued(credential, password, expiresAt);
    }

    private Credential createAccount(String email, String rawPassword, String firstName,
                                     String lastName, String role,
                                     Credential.Status status, Instant temporaryUntil) {

        UserProfileView profile = createProfile(
                new InternalUserUpsert(email, firstName.trim(), lastName.trim(), List.of(role)));

        Instant now = Instant.now();
        try {
            Credential account = Credential.newLocalAccount(
                    profile.id(), email, passwordEncoder.encode(rawPassword),
                    List.of(role), status, now);
            if (temporaryUntil != null) {
                account = account.withTemporaryPassword(account.passwordHash(), temporaryUntil, now);
            }
            Credential credential = credentials.save(account);

            log.info("Registered {} as {} with status {}", email, role, credential.status());
            return credential;
        } catch (DuplicateKeyException e) {
            // The unique index did its job: another request won the race between the
            // pre-check and this insert.
            throw new DuplicateResourceException("Credential", email);
        }
    }

    /**
     * {@code emailVerified} is false either way - nothing has been proven at this point.
     * Only whether the account may sign in before it proves anything is configurable.
     */
    private Credential.Status initialStatus() {
        return properties.requireEmailVerification()
                ? Credential.Status.PENDING_VERIFICATION
                : Credential.Status.ACTIVE;
    }

    private UserProfileView createProfile(InternalUserUpsert body) {
        try {
            UserProfileView profile = userProfiles.upsert(body);
            if (profile == null || profile.id() == null || profile.id().isBlank()) {
                throw new IllegalStateException("user-service returned no profile id");
            }
            return profile;
        } catch (RuntimeException e) {
            // Never surfaces as 500: nothing the caller sent was wrong, and trying again
            // in a moment is genuinely likely to work.
            throw new ProfileServiceUnavailableException(
                    "Registration is temporarily unavailable. Please try again in a moment.", e);
        }
    }

    private void requireAllowedDomain(String email) {
        boolean allowed = properties.allowedEmailDomains().stream()
                .anyMatch(domain -> email.endsWith("@" + domain));
        if (allowed) {
            return;
        }
        String suffixes = properties.allowedEmailDomains().stream()
                .map(domain -> "@" + domain)
                .collect(java.util.stream.Collectors.joining(" or "));
        throw new BusinessRuleException(
                "Registration requires a " + suffixes + " address",
                List.of(new ApiError.FieldIssue("email", "Must use " + suffixes)));
    }

    private void requireAvailable(String email) {
        if (credentials.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Credential", email);
        }
    }

    private RegistrationResponse respond(Credential credential) {
        return new RegistrationResponse(credential.userId(), credential.email(), false);
    }


    /**
     * Lower-cased on the way in, everywhere. The unique index is on the stored form, and
     * user-service lower-cases on its side too; if the two ever disagreed, a retry
     * differing only in capitalisation would create a duplicate profile.
     */
    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
