package co.edu.konradlorenz.kapp.map.domain;

/**
 * Whether a floor, or one space on it, can be reached without climbing stairs.
 *
 * <p>Three values, not a boolean, because most of the campus has not been checked yet. A
 * boolean would have to default to one of its two answers, and either default is a claim
 * nobody made: {@code true} sends a wheelchair user to the JAAB mezzanine, which its plan draws
 * without a lift, and {@code false} brands the whole campus inaccessible. {@code UNKNOWN} is
 * the honest starting point, and a space inherits its floor's value until someone who walked it
 * says otherwise.
 */
public enum Accessibility {
    /** A lift or a ramp reaches it; no step on the way. */
    STEP_FREE,
    /** Only stairs reach it: the JAAB mezzanine, MU's first floor, EC's north terrace. */
    STAIRS_ONLY,
    /** Nobody has checked yet. The default, and never read as either of the other two. */
    UNKNOWN
}
