package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A kind of space - "Aula", "Cuarto de TI", "Sala de lactancia" - in the catalogue the portal
 * edits.
 *
 * <p>The catalogue is data rather than an enum because the survey keeps finding kinds of room
 * nobody listed in advance, and each one would otherwise be a release. What the clients rely on
 * is the {@link SpaceCategory}, which is closed; a type they have never seen still arrives with
 * a category they know how to draw.
 *
 * @param code the identifier spaces store, uppercase: {@code CLASSROOM}, {@code IT_ROOM}. It is
 *             the document id, so it is unique by construction and cannot be renamed - a rename
 *             would have to touch every space that uses it. Its display {@code name} is what
 *             changes
 * @param name Spanish display name
 */
@Document(collection = "spaceTypes")
public record SpaceTypeDocument(
        @Id String code,
        String name,
        SpaceCategory category,
        Instant createdAt,
        Instant updatedAt
) {
}
