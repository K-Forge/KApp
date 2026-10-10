package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.client.UserProfileClient;
import co.edu.konradlorenz.kapp.auth.client.UserProfileView;
import co.edu.konradlorenz.kapp.auth.config.RegistrationProperties;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.domain.InvitationCode;
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
 * <p>The invitation code is claimed before either, and returned if anything after it
 * fails, so a crashed registration does not silently consume an intake slot.
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

    /** How many of a student code's first digits are its program's: 506900001 is a 506. */
    static final int PROGRAM_DIGITS = 3;

    private final CredentialRepository credentials;
    private final InvitationCodeService invitationCodes;
    private final UserProfileClient userProfiles;
    private final VerificationService verification;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties properties;

    public RegistrationService(CredentialRepository credentials,
                               InvitationCodeService invitationCodes,
                               UserProfileClient userProfiles,
                               VerificationService verification,
                               PasswordEncoder passwordEncoder,
                               RegistrationProperties properties) {
        this.credentials = credentials;
        this.invitationCodes = invitationCodes;
        this.userProfiles = userProfiles;
        this.verification = verification;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /** Institutional signup. The invitation code decides the role; this method never grants admin. */
    public RegistrationResponse register(RegistrationRequest request) {
        String email = normalise(request.email());
        requireAllowedDomain(email);
        requireAvailable(email);

        InvitationCode code = invitationCodes.redeem(request.invitationCode());

        // roleFrom is deliberately inside this try, not between redeem and it: a code
        // whose role is rejected - a rogue ROLE_ADMIN entry, say - has still claimed a
        // slot by this point, and that slot must come back just like any other failure
        // past this line. Checking the role before redeeming would be safer-looking but
        // reopens the read-then-write race redeem() exists to close.
        try {
            String role = roleFrom(code);
            InternalUserUpsert.AcademicInfo academic = academicFor(role, request.studentCode(), request.programCode());
            Credential credential = createAccount(email, request.password(),
                    request.firstName(), request.lastName(), role, academic, initialStatus(), null);

            verification.issueAndSend(credential);
            return respond(credential);
        } catch (RuntimeException e) {
            // The slot was claimed for a registration that did not happen. Give it back,
            // or a failed attempt permanently costs the registrar one account.
            invitationCodes.release(code.code());
            throw e;
        }
    }

    /**
     * An account an administrator creates, with a temporary password the person replaces at
     * first sign-in. The same rules as registration - the domain, a student's codes, never
     * ROLE_ADMIN - minus the invitation code: the administrator is the invitation. It may sign
     * in at once, since somebody vouched for it, but its address is still not verified.
     */
    public PasswordService.Issued createWithTemporaryPassword(AccountRequest request) {
        String email = normalise(request.email());
        requireAllowedDomain(email);
        requireAvailable(email);
        String role = request.role();
        if (!KappRoles.STUDENT.equals(role) && !KappRoles.PROFESSOR.equals(role)) {
            throw new BusinessRuleException("An account can be created as a student or a professor",
                    List.of(new ApiError.FieldIssue("role", "ROLE_STUDENT or ROLE_PROFESSOR")));
        }
        InternalUserUpsert.AcademicInfo academic = academicFor(role, request.studentCode(), request.programCode());
        String password = TemporaryPasswords.next();
        Instant expiresAt = Instant.now().plus(TemporaryPasswords.LIFETIME);
        Credential credential = createAccount(email, password, request.firstName(), request.lastName(),
                role, academic, Credential.Status.ACTIVE, expiresAt);
        return new PasswordService.Issued(credential, password, expiresAt);
    }

    private Credential createAccount(String email, String rawPassword, String firstName,
                                     String lastName, String role,
                                     InternalUserUpsert.AcademicInfo academic,
                                     Credential.Status status, Instant temporaryUntil) {

        UserProfileView profile = createProfile(
                new InternalUserUpsert(email, firstName.trim(), lastName.trim(), role, academic));

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

    /**
     * A staff invitation carries no student code, and user-service requires an academic
     * record for a student and forbids one for a guest.
     *
     * <p>The program may be left out: the university's student codes begin with their
     * program's three digits - 506900001 is a 506, Ingeniería de Sistemas - so it is read from
     * there. One sent anyway is kept as sent.
     */
    private InternalUserUpsert.AcademicInfo academicFor(String role, String studentCode, String programCode) {
        if (!KappRoles.STUDENT.equals(role)) {
            return null;
        }
        if (isBlank(studentCode)) {
            throw new BusinessRuleException(
                    "A student account requires studentCode",
                    List.of(new ApiError.FieldIssue("studentCode", "Required for a student")));
        }
        if (isBlank(programCode)) {
            programCode = studentCode.trim().substring(0, PROGRAM_DIGITS);
        }
        // pensumCode and currentLevel are not in the registration contract but are
        // mandatory on the profile, so the deployment supplies them. A new student starts
        // on the current pensum, at semester one.
        return new InternalUserUpsert.AcademicInfo(
                studentCode.trim(), programCode.trim(),
                properties.defaultPensumCode(), properties.defaultCurrentLevel());
    }

    /** ROLE_ADMIN is never reachable through signup, whatever a code claims. */
    private String roleFrom(InvitationCode code) {
        String role = code.role();
        if (KappRoles.STUDENT.equals(role) || KappRoles.PROFESSOR.equals(role)) {
            return role;
        }
        log.error("Invitation code {} carries role {}, which registration must not grant",
                code.code(), role);
        throw new BusinessRuleException("Invitation code is invalid, expired or already used",
                List.of(new ApiError.FieldIssue("invitationCode", "Unknown, expired or already used")));
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
