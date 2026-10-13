package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

/** What stands on each campus's blocks besides the university, one document per campus. */
public interface CampusStructuresRepository extends MongoRepository<CampusStructuresDocument, String> {
}
