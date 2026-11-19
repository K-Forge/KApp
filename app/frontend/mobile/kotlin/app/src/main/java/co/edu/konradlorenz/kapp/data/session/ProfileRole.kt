package co.edu.konradlorenz.kapp.data.session

/**
 * The one profile role an account holds, out of the `roles` list the API sends.
 *
 * `roles` mixes the profile role with permissions (`["ROLE_STAFF", "ROLE_ADMIN"]`). Permissions
 * open parts of the admin portal and change nothing in the app, so only the profile role is kept
 * here: it is what decides the tabs (issue #46, and "Roles" in docs/api/auth.openapi.yaml).
 */
enum class ProfileRole(val apiName: String) {
    Student("ROLE_STUDENT"),
    Professor("ROLE_PROFESSOR"),
    Staff("ROLE_STAFF"),

    /**
     * A university account the directory did not classify as any of the three (#249). It should
     * not happen; when it does, the person gets the map and is told to contact TI.
     */
    Unassigned("ROLE_UNASSIGNED"),

    /**
     * A visitor with a day pass (ADR 0007). The token only opens the map, lasts 24 hours and does
     * not renew; there is no profile behind it, so `GET /api/users/me` is never asked for (#232).
     */
    Guest("ROLE_GUEST"),
    ;

    companion object {
        /**
         * The profile role in [roles], or `null` when there is none - a list of permissions alone,
         * or a role this build does not know yet. The caller decides what that means; it is not
         * quietly turned into a student.
         */
        fun of(roles: List<String>): ProfileRole? =
            roles.firstNotNullOfOrNull { role -> entries.firstOrNull { it.apiName == role } }
    }
}
