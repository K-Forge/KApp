package co.edu.konradlorenz.kapp.map.domain;

/**
 * How far a floor is from being a floor anyone can trust.
 *
 * <p>The survey of the campus arrives in three states, and they are not interchangeable. A floor
 * transcribed from an evacuation plan looks exactly as finished as one somebody walked with the
 * plan in hand, yet the plans draw the building's outline on every storey - the central building
 * shows its auditorium on all eight. Keeping the state on the floor is what lets the portal show
 * what is still left to visit, and what lets a client say "este piso aún no está verificado"
 * instead of presenting a guess as a fact.
 */
public enum FloorStatus {
    /** The floor exists but nothing is drawn: a reception with no plan, a basement nobody visited. */
    UNMAPPED,
    /** Drawn from photographs of posted plans. Probably right in shape, unconfirmed in use. */
    DRAFT,
    /** Walked and corrected on site. */
    VERIFIED
}
