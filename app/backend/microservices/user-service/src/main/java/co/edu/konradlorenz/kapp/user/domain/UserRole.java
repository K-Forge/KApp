package co.edu.konradlorenz.kapp.user.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * A role an account holds.
 *
 * <p>An account holds exactly one <em>profile role</em> - {@link #ROLE_STUDENT},
 * {@link #ROLE_PROFESSOR} or {@link #ROLE_STAFF} - which decides what the app shows, and any number
 * of <em>permissions</em>, which open parts of the admin portal. All of them travel together in the
 * {@code roles} claim and in a profile's {@code roles}.
 *
 * <p>The constants carry the {@code ROLE_} prefix in their own names on purpose: the JWT
 * {@code roles} claim, the OpenAPI enum and this type then all spell the value the same way, so
 * Jackson needs no custom naming. See {@code KappRoles} in {@code common} for the authority names.
 *
 * <p>{@link #ROLE_GUEST} is kept only so a profile written before user 1.0 still reads: a visitor
 * now holds a day pass, not an account, and no new profile takes it.
 */
public enum UserRole {
    ROLE_STUDENT,
    ROLE_PROFESSOR,
    ROLE_STAFF,
    ROLE_ADMIN,
    ROLE_RECEPTION,
    ROLE_MAINTENANCE,
    ROLE_MODERATION,
    ROLE_WELLBEING,
    ROLE_GUEST;

    public static final Set<UserRole> PROFILE_ROLES = EnumSet.of(ROLE_STUDENT, ROLE_PROFESSOR, ROLE_STAFF);

    public boolean isProfileRole() {
        return PROFILE_ROLES.contains(this);
    }
}
