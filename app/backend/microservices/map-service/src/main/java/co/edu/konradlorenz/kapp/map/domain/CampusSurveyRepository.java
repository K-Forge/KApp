package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

/** The distances taken round each campus's blocks, one document per campus. */
public interface CampusSurveyRepository extends MongoRepository<CampusSurveyDocument, String> {
}
