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

    /**
     * A member the Microsoft directory does not classify, and no administrator has yet. Reads the
     * map and their own profile, and nothing academic, until an administrator gives them one of the
     * other profile roles in the portal or the directory classifies them.
     */
    public static final String UNASSIGNED = "ROLE_UNASSIGNED";

    /** Manages KApp: accounts, their roles and permissions, and the campus map. */
    public static final String ADMIN = "ROLE_ADMIN";

    /** Reception: issues visitors their day passes and keeps the visitor register. */
    public static final String RECEPTION = "ROLE_RECEPTION";

    /** Maintenance: validates damage reports. */
    public static final String MAINTENANCE = "ROLE_MAINTENANCE";

    /** Moderation: reviews what members publish and what they report. */
    public static final String MODERATION = "ROLE_MODERATION";

    /** Bienestar: events, clubs and points. */
    public static final String WELLBEING = "ROLE_WELLBEING";

    /**
     * The roles that decide what the app shows. An account holds exactly one, and the Microsoft
     * directory gives it; {@link #UNASSIGNED} when the directory says nothing.
     */
    public static final java.util.List<String> PROFILE_ROLES = java.util.List.of(STUDENT, PROFESSOR, STAFF, UNASSIGNED);

    /**
     * The profile roles somebody can be given: by an administrator, or to an account created in
     * the portal. {@link #UNASSIGNED} is not one: it is what an account holds
     * before anybody chose.
     */
    public static final java.util.List<String> ASSIGNABLE_PROFILE_ROLES = java.util.List.of(STUDENT, PROFESSOR, STAFF);

    /**
     * The roles that open parts of the admin portal, on top of the profile role. An administrator
     * grants them; nobody gets one by signing in.
     */
    public static final java.util.List<String> PERMISSIONS =
            java.util.List.of(ADMIN, RECEPTION, MAINTENANCE, MODERATION, WELLBEING);

    /** Bare names for {@code hasRole(...)}, which prepends {@code ROLE_} itself. */
    public static final class Short {
        public static final String GUEST = "GUEST";
        public static final String STUDENT = "STUDENT";
        public static final String PROFESSOR = "PROFESSOR";
        public static final String STAFF = "STAFF";
        public static final String UNASSIGNED = "UNASSIGNED";
        public static final String ADMIN = "ADMIN";
        public static final String RECEPTION = "RECEPTION";
        public static final String MAINTENANCE = "MAINTENANCE";
        public static final String MODERATION = "MODERATION";
        public static final String WELLBEING = "WELLBEING";

        private Short() {
        }
    }

    private KappRoles() {
    }
}
