package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * The distances taken round a campus's blocks, in one document: the portal saves them whole, as
 * it saves the structures, and the version tells two saves apart.
 *
 * @param id      the campus's name ignoring case and accents, as the ground and the structures match it
 * @param campus  the campus's name as it was first written
 * @param version bumped by every save; a save that sends an older one is refused
 */
@Document(collection = "map_surveys")
public record CampusSurveyDocument(
        @Id String id,
        String campus,
        List<SurveyMeasure> measures,
        long version,
        Instant updatedAt
) {
}
