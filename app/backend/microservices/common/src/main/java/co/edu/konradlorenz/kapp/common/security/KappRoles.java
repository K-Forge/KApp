package co.edu.konradlorenz.kapp.common.security;

/**
 * The authority names KApp issues and checks.
 *
 * <p>The JWT carries them already prefixed, e.g. {@code "roles": ["ROLE_STUDENT"]}, so
 * {@code KappSecurityAutoConfiguration} maps them with an EMPTY authority prefix. Adding
 * {@code ROLE_} again there would produce {@code ROLE_ROLE_STUDENT}, and
 * {@code hasRole('STUDENT')} would silently never match.
 */
public final class KappRoles {

    /** Someone with no university account. May read the campus map and nothing else. */
    public static final String GUEST = "ROLE_GUEST";

    public static final String STUDENT = "ROLE_STUDENT";
    public static final String PROFESSOR = "ROLE_PROFESSOR";

    /**
     * A member of the university's administrative staff. Reads what every member reads - the map,
     * the catalog - and takes and teaches no classes, so has no timetable and no semáforo.
     */
    public static final String STAFF = "ROLE_STAFF";

    public static final String ADMIN = "ROLE_ADMIN";

    /** Bare names for {@code hasRole(...)}, which prepends {@code ROLE_} itself. */
    public static final class Short {
        public static final String GUEST = "GUEST";
        public static final String STUDENT = "STUDENT";
        public static final String PROFESSOR = "PROFESSOR";
        public static final String STAFF = "STAFF";
        public static final String ADMIN = "ADMIN";

        private Short() {
        }
    }

    private KappRoles() {
    }
}
