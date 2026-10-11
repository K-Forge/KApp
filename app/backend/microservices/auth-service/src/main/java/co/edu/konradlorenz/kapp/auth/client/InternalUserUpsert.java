package co.edu.konradlorenz.kapp.auth.client;

import java.util.List;

/**
 * Body of {@code POST /internal/users}, matching {@code docs/api/user.openapi.yaml} 1.0: the names
 * an account goes by and the roles it holds.
 *
 * <p>Auth-service owns credentials and nothing else, so this carries only what user-service needs
 * to materialise a profile, and since user 1.0 nothing academic: user-service keeps no student code,
 * and reads a student's program, pensum and level from SINU. The e-mail is the natural key of the
 * upsert and <strong>must be lower-cased before it is sent</strong>: user-service lower-cases on its
 * side too, and if the two ever disagree a retry differing only in capitalisation slips past the
 * unique index and creates exactly the duplicate the upsert exists to prevent.
 *
 * @param roles the profile role and any permissions; user-service refuses a list without exactly one
 *              profile role with 400
 */
public record InternalUserUpsert(
        String email,
        String firstName,
        String lastName,
        List<String> roles
) {
}
