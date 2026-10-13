package co.edu.konradlorenz.kapp.map.domain;

import java.util.List;

/**
 * Something on a campus's blocks that is not the university's: a neighbour's building, a heritage
 * house with its garden. It is drawn around the buildings so that they read in their place, and
 * edited in the portal while a block is surveyed.
 *
 * @param name      what it is called, or what it is
 * @param floors    how many floors it rises; 0 for ground, a garden
 * @param basements how many floors it goes below the street
 * @param lot       the cadastral lot it stands on, when known
 * @param ring      its outline, [lon, lat] points, the first repeated at the end
 */
public record Structure(String name, int floors, int basements, String lot, List<List<Double>> ring) {
}
