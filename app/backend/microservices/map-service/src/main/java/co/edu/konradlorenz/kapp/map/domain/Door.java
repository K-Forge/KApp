package co.edu.konradlorenz.kapp.map.domain;

/**
 * A way into a space: the stretch of its outline the door takes up, from one jamb to the other.
 *
 * <p>Both ends lie on the space's own outline. A door between two rooms is recorded on both,
 * each on its own wall. Where the door is is what a route needs, and what tells two rooms that
 * share a wall apart: which one opens onto the corridor.
 */
public record Door(Point from, Point to) {
}
