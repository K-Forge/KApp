package co.edu.konradlorenz.kapp.auth.identity;

import java.util.Optional;

/**
 * Who a verified Microsoft ID token says the person is.
 *
 * @param objectId    {@code oid}: the person in the tenant, stable for life, unlike the address
 * @param email       {@code email}, or {@code preferred_username} without it; lowercased
 * @param givenName   {@code given_name}, when the token carries it
 * @param familyName  {@code family_name}, when the token carries it
 * @param displayName {@code name}, the whole name as the directory writes it
 * @param profile     what the directory says the person is; empty when the profile claim is missing
 *                    or holds no value KApp knows
 */
public record MicrosoftIdentity(
        String objectId,
        String email,
        String givenName,
        String familyName,
        String displayName,
        Optional<DirectoryProfile> profile
) {

    /**
     * The profiles the directory can give, in the order that decides between two: somebody who is
     * both studying and teaching is a student first.
     */
    public enum DirectoryProfile {
        STUDENT,
        PROFESSOR,
        STAFF
    }
}
