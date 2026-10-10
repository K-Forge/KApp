package co.edu.konradlorenz.kapp.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One refresh token: what renews a session without asking the person anything.
 *
 * <p>The token itself is opaque and never stored. What is kept is its SHA-256, so a copy of this
 * collection opens no session.
 *
 * <h2>Families</h2>
 * Every sign-in starts a family, and every renewal adds the next token to it while marking the one
 * presented as used. A used token presented again means two parties hold the same session - the
 * person and whoever copied it - and nothing here can tell which is which, so the whole family is
 * revoked and both have to sign in again.
 *
 * @param tokenHash SHA-256 of the token, hex encoded; unique
 * @param familyId  shared by every token descending from one sign-in
 * @param userId    the account, the {@code sub} of the access tokens it renews
 * @param client    which client holds it, which decides how long it lasts
 * @param expiresAt when it stops renewing if it is not used first. The TTL index removes the
 *                  document once it has passed, used or not
 * @param usedAt    when it was exchanged for the next one; null while it is the current token
 * @param revokedAt when its family was revoked - sign-out, reuse or a suspended account
 */
@Document(collection = RefreshToken.COLLECTION)
public record RefreshToken(
        @Id String id,
        String tokenHash,
        String familyId,
        String userId,
        Client client,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt
) {

    public static final String COLLECTION = "refresh_tokens";

    /** Which client signed in. The portal's session is a working day; the apps' a month. */
    public enum Client {
        APP,
        PORTAL
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
