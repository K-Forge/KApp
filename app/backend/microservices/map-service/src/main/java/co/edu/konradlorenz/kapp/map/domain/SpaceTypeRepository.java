package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** The space type catalogue, keyed by its code. A few dozen documents at most. */
public interface SpaceTypeRepository extends MongoRepository<SpaceTypeDocument, String> {

    List<SpaceTypeDocument> findByCategory(SpaceCategory category);
}
