package co.edu.konradlorenz.kapp.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * What a person needs in order to sign in. Nothing else.
 *
 * <p>Profile data - name, programme, semester, avatar - lives in user-service. The two
 * used to be the same four tables mapped twice by two services, which is the blurred
 * boundary {@code docs/SECURITY-AUDIT.md} flagged. Keeping credentials alone in this
 * service is also what makes the planned Entra ID migration tractable: this collection
 * is the piece that disappears when the university becomes the identity provider, and
 * nothing else has to move.
 *
 * <h2>{@code emailVerified} and {@code status} are different facts</h2>
 * {@code emailVerified} records whether the address was ever actually proven. It is
 * {@code false} for every freshly registered account, without exception, because at that
 * moment nothing has been proven. {@code status} records whether the account may sign in,
 * and that is a policy decision: {@link Status#ACTIVE} when the deployment does not
 * require verification, {@link Status#PENDING_VERIFICATION} when it does.
 *
 * <p>Sign-in gates on {@code status}, never on {@code emailVerified}. Collapsing the two
 * - creating accounts as verified so they can log in while there is no SMTP relay - makes
 * an account claim a verification that never happened, and once the flag is switched on
 * those accounts are indistinguishable from genuinely verified ones.
 *
 * @param userId                     shared identifier with user-service; becomes the {@code sub} claim
 * @param email                      the login name, unique, stored lower-cased
 * @param passwordHash               BCrypt; null once an external identity provider owns this account
 * @param roles                      prefixed role names, e.g. {@code ROLE_STUDENT}
 * @param status                     {@link Status}
 * @param emailVerified              whether the address has been confirmed
 * @param provider                   which identity provider owns this credential
 * @param verificationTokenHash      SHA-256 of the outstanding verification token, never the
 *                                   token itself; null once consumed. Stored under the field
 *                                   name {@code verificationToken}, which
 *                                   {@code V001_AuthIndexes} already indexes.
 * @param verificationTokenExpiresAt when that token stops being accepted
 * @param passwordChangeRequired     true while the password is a temporary one an administrator
 *                                   issued: sign-in refuses it until the person sets their own.
 *                                   Null on every account older than temporary passwords, which
 *                                   reads as false
 * @param temporaryPasswordExpiresAt when that temporary password stops being accepted at all
 */
@Document(collection = "credentials")
public record Credential(
        @Id String id,
        String userId,
        String email,
        String passwordHash,
        List<String> roles,
        Status status,
        boolean emailVerified,
        Provider provider,
        @Field("verificationToken") String verificationTokenHash,
        Instant verificationTokenExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Boolean passwordChangeRequired,
        Instant temporaryPasswordExpiresAt
) {

    public enum Status {
        ACTIVE,
        /** Registered but the e-mail has not been confirmed yet. */
        PENDING_VERIFICATION,
        /** Disabled by an administrator. */
        SUSPENDED
    }

    /**
     * Which system vouches for this identity. {@code LOCAL} is the MVP; {@code ENTRA_ID}
     * is what the university's Microsoft tenant will provide once an application
     * registration is granted.
     */
    public enum Provider {
        LOCAL,
        ENTRA_ID
    }

    public boolean canSignIn() {
        return status == Status.ACTIVE;
    }

    /** Whether the password is a temporary one, to be replaced before it signs anybody in. */
    public boolean mustChangePassword() {
        return Boolean.TRUE.equals(passwordChangeRequired);
    }

    /** A temporary password past its expiry: it no longer proves anything. */
    public boolean temporaryPasswordExpired(Instant now) {
        return mustChangePassword() && temporaryPasswordExpiresAt != null && now.isAfter(temporaryPasswordExpiresAt);
    }

    /** A temporary password an administrator issued, replacing whatever the account had. */
    public Credential withTemporaryPassword(String hash, Instant expiresAt, Instant now) {
        return new Credential(id, userId, email, hash, roles, status, emailVerified, provider,
                verificationTokenHash, verificationTokenExpiresAt, createdAt, now, true, expiresAt);
    }

    /** The person's own password: whatever was temporary about the last one is over. */
    public Credential withPassword(String hash, Instant now) {
        return new Credential(id, userId, email, hash, roles, status, emailVerified, provider,
                verificationTokenHash, verificationTokenExpiresAt, createdAt, now, null, null);
    }

    /**
     * Suspends or restores the account, for an administrator's deactivation.
     *
     * <p>Restoring lands on {@code ACTIVE} rather than on whatever the status was before.
     * Storing the previous one would need a field and a migration, and the case it would
     * protect - an account suspended while its e-mail was still unverified coming back
     * verified - is a human decision by an administrator either way. It matters only once
     * e-mail verification is switched on; {@code SECURITY-AUDIT.md} records it as such.
     *
     * <p>Not conditioned on {@code emailVerified}: verification is off in the MVP, so every
     * account carries {@code emailVerified = false}, and treating that as "send it back to
     * PENDING_VERIFICATION" would make deactivation a one-way door for all of them.
     *
     * <p>Returns {@code this} when nothing would change, so an idempotent call writes nothing.
     */
    public Credential withSignInAllowed(boolean allowed, Instant now) {
        Status target = allowed ? Status.ACTIVE : Status.SUSPENDED;
        if (status == target) {
            return this;
        }
        return new Credential(id, userId, email, passwordHash, roles, target, emailVerified,
                provider, verificationTokenHash, verificationTokenExpiresAt, createdAt, now,
                passwordChangeRequired, temporaryPasswordExpiresAt);
    }

    /**
     * A brand new local account.
     *
     * <p>{@code emailVerified} is hard-coded to {@code false} here rather than taken as a
     * parameter: there is no path through registration that legitimately produces a
     * verified address, so the type refuses to express one.
     */
    public static Credential newLocalAccount(String userId, String email, String passwordHash,
                                             List<String> roles, Status status, Instant now) {
        return new Credential(null, userId, email, passwordHash, roles, status, false,
                Provider.LOCAL, null, null, now, now, null, null);
    }
}
