package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Everything on a campus's blocks that is not the university's, in one document: the portal saves
 * the list whole, as it saves a floor's layout whole, and the version tells two saves apart.
 *
 * @param id         the campus's name ignoring case and accents, as the ground and the building
 *                   search match it
 * @param campus     the campus's name as it was first written
 * @param version    bumped by every save; a save that sends an older one is refused
 */
@Document(collection = "map_structures")
public record CampusStructuresDocument(
        @Id String id,
        String campus,
        List<Structure> structures,
        long version,
        Instant updatedAt
) {
}
