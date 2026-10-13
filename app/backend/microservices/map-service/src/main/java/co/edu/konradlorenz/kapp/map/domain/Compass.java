package co.edu.konradlorenz.kapp.map.domain;

/**
 * A direction on the ground, for which way a floor's drawing faces.
 *
 * <p>The plans on the walls are not drawn north up: the Edificio Central's put Calle 63, to the
 * north, on the left. A floor records which of these its drawing's top edge faces, so a client
 * can turn it north up, or show a compass that points the right way.
 */
public enum Compass {
    NORTH,
    EAST,
    SOUTH,
    WEST
}
