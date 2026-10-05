package co.edu.konradlorenz.kapp.map.domain;

/**
 * Where a building's drawing lies on the ground.
 *
 * <p>Every floor of a building is drawn in one frame, in the building's own units, as the plans on
 * its walls hang. This lays that frame on the earth: the frame's top-left corner is at
 * {@code origin}, its top edge faces {@code bearing} and one unit of it is
 * {@code metresPerUnit} long. With it a client can draw the streets under a floor, or every
 * building of a campus on one map.
 *
 * <p>For a point {@code (x, y)} of the drawing, the offset from the origin on the ground is
 * {@code east = m (x cos b - y sin b)} and {@code north = -m (x sin b + y cos b)}, with
 * {@code m = metresPerUnit} and {@code b = bearing}: up the drawing is toward {@code b}, and to
 * its right toward {@code b + 90}. A campus is small enough for the plane through its middle to
 * stand in for the earth.
 *
 * @param bearing       degrees clockwise from true north that the drawing's top edge faces,
 *                      {@code [0, 360)}. The plans are seldom drawn to a quarter turn, which is
 *                      all a floor's {@code top} records
 * @param metresPerUnit how long one unit of the drawing is on the ground
 */
public record Placement(GeoPoint origin, double bearing, double metresPerUnit) {
}
