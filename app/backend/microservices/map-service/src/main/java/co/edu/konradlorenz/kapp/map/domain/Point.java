package co.edu.konradlorenz.kapp.map.domain;

/**
 * A point on a floor's drawing, in the floor's own units.
 *
 * <p>The origin is the top-left corner of the floor as the plan is displayed, x to the right and
 * y down, the way the evacuation plans hang on the wall. Units are whole numbers and carry no
 * physical size of their own: a floor is {@code width} by {@code height} of them, and a floor
 * traced from a photo is usually measured in the photo's pixels once straightened.
 */
public record Point(int x, int y) {
}
